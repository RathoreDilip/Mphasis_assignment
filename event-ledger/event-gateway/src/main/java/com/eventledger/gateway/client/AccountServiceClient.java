package com.eventledger.gateway.client;

import com.eventledger.gateway.filter.TraceIdFilter;
import com.eventledger.gateway.model.EventRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class AccountServiceClient {

    private final RestTemplate restTemplate;
    private final String accountServiceUrl;

    public AccountServiceClient(RestTemplate restTemplate,
                                @Value("${account-service.url}") String accountServiceUrl) {
        this.restTemplate = restTemplate;
        this.accountServiceUrl = accountServiceUrl;
    }

    // ── Apply Transaction ─────────────────────────────────────────────────────

    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
    @Retry(name = "accountService")
    public ResponseEntity<Map> applyTransaction(EventRequest request, String traceId) {
        String url = accountServiceUrl + "/accounts/" + request.getAccountId() + "/transactions";

        HttpHeaders headers = buildHeaders(traceId);
        Map<String, Object> body = Map.of(
                "eventId",         request.getEventId(),
                "type",            request.getType(),
                "amount",          request.getAmount(),
                "currency",        request.getCurrency(),
                "eventTimestamp",  request.getEventTimestamp().toString()
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.info("Calling Account Service applyTransaction for accountId={} traceId={}",
                request.getAccountId(), traceId);

        return restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
    }

    public ResponseEntity<Map> applyTransactionFallback(EventRequest request,
                                                        String traceId,
                                                        Throwable ex) {
        log.error("Circuit breaker OPEN — Account Service unavailable. accountId={} traceId={} error={}",
                request.getAccountId(), traceId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error",   "Account Service is currently unavailable. Please retry later.",
                        "traceId", traceId
                ));
    }

    // ── Get Balance ───────────────────────────────────────────────────────────

    @CircuitBreaker(name = "accountService", fallbackMethod = "getBalanceFallback")
    @Retry(name = "accountService")
    public ResponseEntity<Map> getBalance(String accountId, String traceId) {
        String url = accountServiceUrl + "/accounts/" + accountId + "/balance";
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(traceId));

        log.info("Calling Account Service getBalance for accountId={} traceId={}", accountId, traceId);
        return restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
    }

    public ResponseEntity<Map> getBalanceFallback(String accountId, String traceId, Throwable ex) {
        log.error("Circuit breaker OPEN — Cannot fetch balance. accountId={} traceId={} error={}",
                accountId, traceId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error",   "Account Service is unreachable. Balance unavailable.",
                        "traceId", traceId
                ));
    }

    // ── Get Account Details ───────────────────────────────────────────────────

    @CircuitBreaker(name = "accountService", fallbackMethod = "getAccountFallback")
    @Retry(name = "accountService")
    public ResponseEntity<Map> getAccount(String accountId, String traceId) {
        String url = accountServiceUrl + "/accounts/" + accountId;
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(traceId));

        log.info("Calling Account Service getAccount for accountId={} traceId={}", accountId, traceId);
        return restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
    }

    public ResponseEntity<Map> getAccountFallback(String accountId, String traceId, Throwable ex) {
        log.error("Circuit breaker OPEN — Cannot fetch account. accountId={} traceId={} error={}",
                accountId, traceId, ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error",   "Account Service is unreachable. Account details unavailable.",
                        "traceId", traceId
                ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpHeaders buildHeaders(String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TraceIdFilter.TRACE_ID_HEADER, traceId);
        return headers;
    }
}