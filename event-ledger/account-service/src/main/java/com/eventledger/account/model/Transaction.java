package com.eventledger.account.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_event_id",   columnList = "eventId",   unique = true),
        @Index(name = "idx_tx_account_id", columnList = "accountId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Idempotency key — same as gateway eventId
    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String type;              // CREDIT | DEBIT

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    // Original event timestamp — used for out-of-order sorting and balance computation
    @Column(nullable = false)
    private Instant eventTimestamp;

    @Column(nullable = false, updatable = false)
    private Instant processedAt;

    @PrePersist
    public void prePersist() {
        this.processedAt = Instant.now();
    }
}