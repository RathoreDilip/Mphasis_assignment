package com.eventledger.gateway.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_id", columnList = "eventId", unique = true),
        @Index(name = "idx_account_id", columnList = "accountId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "eventId is required")
    private String eventId;

    @Column(nullable = false)
    @NotBlank(message = "accountId is required")
    private String accountId;

    @Column(nullable = false)
    @NotBlank(message = "type is required")
    @Pattern(regexp = "CREDIT|DEBIT", message = "type must be CREDIT or DEBIT")
    private String type;

    @Column(nullable = false, precision = 19, scale = 4)
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0001", message = "amount must be greater than 0")
    private BigDecimal amount;

    @Column(nullable = false)
    @NotBlank(message = "currency is required")
    private String currency;

    @Column(nullable = false)
    @NotNull(message = "eventTimestamp is required")
    private Instant eventTimestamp;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @PrePersist
    public void prePersist() {
        this.receivedAt = Instant.now();
        if (this.status == null) {
            this.status = EventStatus.PROCESSED;
        }
    }
}