package com.eventledger.account.repository;

import com.eventledger.account.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // ── Idempotency check ──────────────────────────────────────────────────────
    boolean existsByEventId(String eventId);

    Optional<Transaction> findByEventId(String eventId);

    // ── Fetch all transactions for account sorted by eventTimestamp ASC ────────
    // This ensures out-of-order events are always returned in chronological order
    List<Transaction> findByAccountIdOrderByEventTimestampAsc(String accountId);

    // ── Compute net balance from all transactions ──────────────────────────────
    // Balance = SUM(CREDIT amounts) - SUM(DEBIT amounts)
    // Uses eventTimestamp-ordered data — arrival order does NOT affect result
    @Query("""
            SELECT COALESCE(
                SUM(CASE WHEN t.type = 'CREDIT' THEN t.amount ELSE -t.amount END),
                0
            )
            FROM Transaction t
            WHERE t.accountId = :accountId
            """)
    BigDecimal computeBalance(@Param("accountId") String accountId);

    // ── Count transactions per type for diagnostics ────────────────────────────
    @Query("""
            SELECT COUNT(t)
            FROM Transaction t
            WHERE t.accountId = :accountId AND t.type = :type
            """)
    long countByAccountIdAndType(@Param("accountId") String accountId,
                                 @Param("type") String type);

    // ── Recent transactions (last 10) for account summary ──────────────────────
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.accountId = :accountId
            ORDER BY t.eventTimestamp DESC
            LIMIT 10
            """)
    List<Transaction> findTop10ByAccountIdOrderByEventTimestampDesc(
            @Param("accountId") String accountId);
}