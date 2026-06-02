package com.eventledger.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
@Slf4j
public class CircuitBreakerConfiguration {

    private static final String ACCOUNT_SERVICE_CB = "accountService";

    /**
     * Programmatic Circuit Breaker config for Account Service calls.
     *
     * State machine:
     *
     *   CLOSED ──(failure rate >= 50%)──► OPEN
     *      ▲                                 │
     *      │                         (wait 10s)
     *      │                                 ▼
     *   CLOSED ◄──(success >= 2/2)── HALF_OPEN
     *
     * - CLOSED   : Normal operation. Calls pass through.
     * - OPEN     : All calls blocked. Fallback is returned immediately.
     * - HALF_OPEN: Limited calls allowed to test if service recovered.
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()

                // ── Sliding Window ─────────────────────────────────────────────
                // Use COUNT_BASED: evaluate last N calls (not time-based)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)                  // evaluate last 5 calls

                // ── Thresholds ─────────────────────────────────────────────────
                .minimumNumberOfCalls(3)               // need at least 3 calls before tripping
                .failureRateThreshold(50.0f)           // trip open if >= 50% fail
                .slowCallRateThreshold(80.0f)          // also trip on slow calls
                .slowCallDurationThreshold(Duration.ofSeconds(3)) // "slow" = > 3s

                // ── Open → Half-Open ───────────────────────────────────────────
                .waitDurationInOpenState(Duration.ofSeconds(10))  // stay open for 10s
                .automaticTransitionFromOpenToHalfOpenEnabled(true)

                // ── Half-Open → Closed/Open ────────────────────────────────────
                .permittedNumberOfCallsInHalfOpenState(2)  // allow 2 probe calls

                // ── Which exceptions count as failures ─────────────────────────
                .recordExceptions(
                        IOException.class,
                        TimeoutException.class,
                        ConnectException.class,
                        ResourceAccessException.class,
                        HttpServerErrorException.class    // 5xx from Account Service
                )

                // ── Which exceptions to IGNORE (not count as failures) ─────────
                .ignoreExceptions(
                        IllegalArgumentException.class   // bad input — not a service fault
                )

                .build();

        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);

        // ── Register event listeners for observability ─────────────────────────
        registerEventListeners(registry);

        return registry;
    }

    /**
     * Named circuit breaker bean — used by @CircuitBreaker(name = "accountService")
     */
    @Bean
    public CircuitBreaker accountServiceCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(ACCOUNT_SERVICE_CB);
    }

    // ── State Transition Logging ───────────────────────────────────────────────

    private void registerEventListeners(CircuitBreakerRegistry registry) {

        registry.getEventPublisher()
                .onEntryAdded(event -> {
                    CircuitBreaker cb = event.getAddedEntry();

                    // Log every state transition
                    cb.getEventPublisher()
                            .onStateTransition(this::logStateTransition)
                            .onCallNotPermitted(e ->
                                    log.warn("[CircuitBreaker] OPEN — call blocked for '{}'. " +
                                                    "All requests returning fallback.",
                                            ACCOUNT_SERVICE_CB))
                            .onError(e ->
                                    log.error("[CircuitBreaker] Call FAILED for '{}' — " +
                                                    "duration={}ms error={}",
                                            ACCOUNT_SERVICE_CB,
                                            e.getElapsedDuration().toMillis(),
                                            e.getThrowable().getMessage()))
                            .onSlowCallRateExceeded(e ->
                                    log.warn("[CircuitBreaker] Slow call rate exceeded for '{}' — " +
                                                    "rate={}%",
                                            ACCOUNT_SERVICE_CB,
                                            e.getSlowCallRate()))
                            .onFailureRateExceeded(e ->
                                    log.warn("[CircuitBreaker] Failure rate exceeded for '{}' — " +
                                                    "rate={}%",
                                            ACCOUNT_SERVICE_CB,
                                            e.getFailureRate()))
                            .onSuccess(e ->
                                    log.debug("[CircuitBreaker] Call SUCCESS for '{}' — duration={}ms",
                                            ACCOUNT_SERVICE_CB,
                                            e.getElapsedDuration().toMillis()));
                });
    }

    private void logStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn("[CircuitBreaker] '{}' state transition: {} → {}",
                ACCOUNT_SERVICE_CB,
                event.getStateTransition().getFromState(),
                event.getStateTransition().getToState());

        switch (event.getStateTransition().getToState()) {
            case OPEN ->
                    log.error("[CircuitBreaker] CIRCUIT OPEN — Account Service calls are BLOCKED. " +
                            "Fallback responses will be returned for next 10 seconds.");
            case HALF_OPEN ->
                    log.warn("[CircuitBreaker] CIRCUIT HALF-OPEN — Sending probe requests " +
                            "to test Account Service recovery.");
            case CLOSED ->
                    log.info("[CircuitBreaker] CIRCUIT CLOSED — Account Service recovered. " +
                            "Normal operation resumed.");
            default ->
                    log.info("[CircuitBreaker] State changed to {}", event.getStateTransition().getToState());
        }
    }
}