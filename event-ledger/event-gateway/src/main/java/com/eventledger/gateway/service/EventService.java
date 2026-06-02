package com.eventledger.gateway.service;

import com.eventledger.gateway.client.AccountServiceClient;
import com.eventledger.gateway.config.EventMapper;
import com.eventledger.gateway.model.*;
import com.eventledger.gateway.repository.EventRepository;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final AccountServiceClient accountServiceClient;
    private final EventMapper eventMapper;
    private final Counter eventReceivedCounter;
    private final Counter eventDuplicateCounter;
    private final Counter eventFailedCounter;
    private final Counter accountServiceErrorCounter;

    // ── Submit Event ──────────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<?> submitEvent(EventRequest request) {
        String traceId = MDC.get("traceId");
        MDC.put("eventId", request.getEventId());
        MDC.put("accountId", request.getAccountId());

        eventReceivedCounter.increment();
        log.info("Processing event eventId={} accountId={} type={} amount={}",
                request.getEventId(), request.getAccountId(),
                request.getType(), request.getAmount());

        // ── Idempotency Check ─────────────────────────────────────────────────
        if (eventRepository.existsByEventId(request.getEventId())) {
            eventDuplicateCounter.increment();
            log.warn("Duplicate event received eventId={} — returning original", request.getEventId());
            Event existing = eventRepository.findByEventId(request.getEventId()).get();
            return ResponseEntity.status(HttpStatus.OK)
                    .body(eventMapper.toResponse(existing));
        }

        // ── Save to Gateway DB ────────────────────────────────────────────────
        Event event = eventMapper.toEntity(request);
        event.setStatus(EventStatus.PENDING);
        Event saved = eventRepository.save(event);
        log.debug("Event saved to gateway DB eventId={}", saved.getEventId());

        // ── Call Account Service ──────────────────────────────────────────────
        ResponseEntity<Map> accountResponse = accountServiceClient.applyTransaction(request, traceId);

        if (accountResponse.getStatusCode().is2xxSuccessful()) {
            saved.setStatus(EventStatus.PROCESSED);
            eventRepository.save(saved);
            log.info("Event processed successfully eventId={}", saved.getEventId());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(eventMapper.toResponse(saved));
        } else {
            // Account Service returned non-2xx (e.g. 503 from circuit breaker fallback)
            accountServiceErrorCounter.increment();
            eventFailedCounter.increment();
            saved.setStatus(EventStatus.FAILED);
            eventRepository.save(saved);
            log.error("Account Service call failed for eventId={} status={}",
                    saved.getEventId(), accountResponse.getStatusCode());
            return ResponseEntity.status(accountResponse.getStatusCode())
                    .body(accountResponse.getBody());
        }
    }

    // ── Get Single Event ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResponseEntity<?> getEvent(String eventId) {
        log.info("Fetching event eventId={}", eventId);
        return eventRepository.findByEventId(eventId)
                .map(event -> ResponseEntity.ok(eventMapper.toResponse(event)))
                .orElseGet(() -> {
                    log.warn("Event not found eventId={}", eventId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(null);
                });
    }

    // ── List Events by Account ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResponseEntity<?> getEventsByAccount(String accountId) {
        log.info("Fetching events for accountId={}", accountId);
        // Sorted by eventTimestamp ASC — handles out-of-order arrival correctly
        List<EventResponse> events = eventRepository
                .findByAccountIdOrderByEventTimestampAsc(accountId)
                .stream()
                .map(eventMapper::toResponse)
                .collect(Collectors.toList());

        log.debug("Found {} events for accountId={}", events.size(), accountId);
        return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "count", events.size(),
                "events", events
        ));
    }
}