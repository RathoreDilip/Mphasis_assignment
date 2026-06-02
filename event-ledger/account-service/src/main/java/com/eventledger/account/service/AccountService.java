package com.eventledger.account.service;

import com.eventledger.account.model.Account;
import com.eventledger.account.model.Transaction;
import com.eventledger.account.model.TransactionRequest;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    // ── Apply Transaction ─────────────────────────────────────────────────────

    @Transactional
    public ResponseEntity<Map<String, Object>> applyTransaction(String accountId,
                                                                TransactionRequest request) {
        log.info("Applying transaction accountId={} eventId={} type={} amount={}",
                accountId, request.getEventId(), request.getType(), request.getAmount());

        // ── Idempotency Check ─────────────────────────────────────────────────
        // If same eventId already processed, return original — do NOT alter balance
        if (transactionRepository.existsByEventId(request.getEventId())) {
            log.warn("Duplicate transaction detected eventId={} — skipping, returning original",
                    request.getEventId());
            Transaction existing = transactionRepository.findByEventId(request.getEventId()).get();
            return ResponseEntity.ok(buildTransactionResponse(existing, "DUPLICATE"));
        }

        // ── Get or create account ─────────────────────────────────────────────
        Account account = accountRepository.findByAccountId(accountId)
                .orElseGet(() -> {
                    log.info("Account not found — auto-creating accountId={}", accountId);
                    Account newAccount = Account.builder()
                            .accountId(accountId)
                            .balance(BigDecimal.ZERO)
                            .build();
                    return accountRepository.save(newAccount);
                });

        // ── Parse eventTimestamp ──────────────────────────────────────────────
        Instant eventTimestamp;
        try {
            eventTimestamp = Instant.parse(request.getEventTimestamp());
        } catch (Exception e) {
            log.error("Invalid eventTimestamp format eventId={} value={}",
                    request.getEventId(), request.getEventTimestamp());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid eventTimestamp format. Expected ISO-8601."));
        }

        // ── Save transaction ──────────────────────────────────────────────────
        // We store the event with its ORIGINAL eventTimestamp.
        // Balance is always recomputed from all stored transactions,
        // so out-of-order arrival does NOT affect correctness.
        Transaction transaction = Transaction.builder()
                .eventId(request.getEventId())
                .accountId(accountId)
                .type(request.getType())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(eventTimestamp)
                .build();

        transactionRepository.save(transaction);
        log.debug("Transaction saved eventId={} accountId={}", request.getEventId(), accountId);

        // ── Recompute balance from ALL transactions ────────────────────────────
        // This approach handles out-of-order events correctly because balance
        // is derived from the full set, not incremented on arrival.
        BigDecimal newBalance = transactionRepository.computeBalance(accountId);
        account.setBalance(newBalance);
        accountRepository.save(account);

        log.info("Balance updated accountId={} newBalance={} eventId={}",
                accountId, newBalance, request.getEventId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(buildTransactionResponse(transaction, "APPLIED"));
    }

    // ── Get Balance ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getBalance(String accountId) {
        log.info("Fetching balance for accountId={}", accountId);

        if (!accountRepository.existsById(accountId)) {
            log.warn("Account not found accountId={}", accountId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "error", "Account not found",
                            "accountId", accountId
                    ));
        }

        BigDecimal balance = transactionRepository.computeBalance(accountId);
        long creditCount = transactionRepository.countByAccountIdAndType(accountId, "CREDIT");
        long debitCount  = transactionRepository.countByAccountIdAndType(accountId, "DEBIT");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("accountId",    accountId);
        response.put("balance",      balance);
        response.put("creditCount",  creditCount);
        response.put("debitCount",   debitCount);
        response.put("timestamp",    Instant.now().toString());

        return ResponseEntity.ok(response);
    }

    // ── Get Account Details ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getAccount(String accountId) {
        log.info("Fetching account details for accountId={}", accountId);

        return accountRepository.findByAccountId(accountId)
                .map(account -> {
                    // Fetch recent 10 transactions sorted by eventTimestamp DESC
                    List<Map<String, Object>> recentTxs = transactionRepository
                            .findTop10ByAccountIdOrderByEventTimestampDesc(accountId)
                            .stream()
                            .map(this::buildTransactionSummary)
                            .collect(Collectors.toList());

                    long creditCount = transactionRepository.countByAccountIdAndType(accountId, "CREDIT");
                    long debitCount  = transactionRepository.countByAccountIdAndType(accountId, "DEBIT");

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("accountId",           accountId);
                    response.put("balance",             account.getBalance());
                    response.put("totalTransactions",   creditCount + debitCount);
                    response.put("creditCount",         creditCount);
                    response.put("debitCount",          debitCount);
                    response.put("createdAt",           account.getCreatedAt().toString());
                    response.put("updatedAt",           account.getUpdatedAt().toString());
                    response.put("recentTransactions",  recentTxs);

                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    log.warn("Account not found accountId={}", accountId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of(
                                    "error",     "Account not found",
                                    "accountId", accountId
                            ));
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> buildTransactionResponse(Transaction tx, String status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId",        tx.getEventId());
        map.put("accountId",      tx.getAccountId());
        map.put("type",           tx.getType());
        map.put("amount",         tx.getAmount());
        map.put("currency",       tx.getCurrency());
        map.put("eventTimestamp", tx.getEventTimestamp().toString());
        map.put("processedAt",    tx.getProcessedAt().toString());
        map.put("status",         status);
        return map;
    }

    private Map<String, Object> buildTransactionSummary(Transaction tx) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId",        tx.getEventId());
        map.put("type",           tx.getType());
        map.put("amount",         tx.getAmount());
        map.put("currency",       tx.getCurrency());
        map.put("eventTimestamp", tx.getEventTimestamp().toString());
        return map;
    }
}