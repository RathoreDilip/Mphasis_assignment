package com.eventledger.account.controller;

import com.eventledger.account.model.TransactionRequest;
import com.eventledger.account.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final AccountService accountService;

    // ── Apply Transaction ─────────────────────────────────────────────────────

    /**
     * POST /accounts/{accountId}/transactions
     *
     * Apply a CREDIT or DEBIT transaction to an account.
     * - Idempotent: same eventId submitted twice → returns original, no balance change.
     * - Out-of-order safe: balance is always recomputed from all stored transactions.
     * - Auto-creates account if it does not exist yet.
     */
    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<Map<String, Object>> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request,
            HttpServletRequest httpRequest) {

        // ── Propagate traceId from Gateway into MDC ───────────────────────────
        String traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put("traceId", traceId);
        }
        MDC.put("accountId", accountId);

        log.info("POST /accounts/{}/transactions received eventId={} type={} amount={}",
                accountId, request.getEventId(), request.getType(), request.getAmount());

        try {
            return accountService.applyTransaction(accountId, request);
        } finally {
            MDC.clear();
        }
    }

    // ── Get Balance ───────────────────────────────────────────────────────────

    /**
     * GET /accounts/{accountId}/balance
     *
     * Returns current net balance for the account.
     * Balance = SUM(CREDITs) - SUM(DEBITs) across ALL transactions.
     */
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<Map<String, Object>> getBalance(
            @PathVariable String accountId,
            HttpServletRequest httpRequest) {

        propagateTrace(httpRequest, accountId);

        log.info("GET /accounts/{}/balance", accountId);

        try {
            return accountService.getBalance(accountId);
        } finally {
            MDC.clear();
        }
    }

    // ── Get Account Details ───────────────────────────────────────────────────

    /**
     * GET /accounts/{accountId}
     *
     * Returns account details including balance, transaction counts,
     * and the 10 most recent transactions ordered by eventTimestamp DESC.
     */
    @GetMapping("/{accountId}")
    public ResponseEntity<Map<String, Object>> getAccount(
            @PathVariable String accountId,
            HttpServletRequest httpRequest) {

        propagateTrace(httpRequest, accountId);

        log.info("GET /accounts/{}", accountId);

        try {
            return accountService.getAccount(accountId);
        } finally {
            MDC.clear();
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Read X-Trace-Id from incoming request and place it into MDC
     * so all log statements in this request include the traceId.
     */
    private void propagateTrace(HttpServletRequest request, String accountId) {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId != null && !traceId.isBlank()) {
            MDC.put("traceId", traceId);
        }
        MDC.put("accountId", accountId);
    }
}