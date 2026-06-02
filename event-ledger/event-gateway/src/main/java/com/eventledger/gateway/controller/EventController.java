package com.eventledger.gateway.controller;

import com.eventledger.gateway.model.EventRequest;
import com.eventledger.gateway.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {

    private final EventService eventService;

    /**
     * POST /events
     * Submit a new transaction event.
     * Handles idempotency — duplicate eventId returns 200 with original.
     * New event returns 201 Created.
     */
    @PostMapping
    public ResponseEntity<?> submitEvent(@Valid @RequestBody EventRequest request) {
        log.debug("POST /events received for eventId={}", request.getEventId());
        return eventService.submitEvent(request);
    }

    /**
     * GET /events/{id}
     * Retrieve a single event by its eventId.
     * Works even when Account Service is unavailable.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEvent(@PathVariable String id) {
        log.debug("GET /events/{}", id);
        return eventService.getEvent(id);
    }

    /**
     * GET /events?account={accountId}
     * List all events for an account, ordered by eventTimestamp ASC.
     * Works even when Account Service is unavailable.
     */
    @GetMapping
    public ResponseEntity<?> getEventsByAccount(@RequestParam String account) {
        log.debug("GET /events?account={}", account);
        return eventService.getEventsByAccount(account);
    }
}