package com.minipay.idempotency;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.minipay.transaction.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {
    @Id
    @Column(name = "idempotency_key")
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation_type", nullable = false)
    private TransactionType operationType;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "to_wallet_id")
    private Long toWalletId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected IdempotencyRecord() {
    }

    public void complete(int responseStatus, String responseBody) {
        if (status != IdempotencyStatus.PROCESSING) {
            throw new IllegalStateException("Only a processing record can be completed");
        }
        if (responseBody == null || responseBody.isBlank()) {
            throw new IllegalArgumentException("Response body must not be blank");
        }

        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        status = IdempotencyStatus.COMPLETED;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public TransactionType getOperationType() {
        return operationType;
    }

    public Long getWalletId() {
        return walletId;
    }

    public Long getToWalletId() {
        return toWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public IdempotencyStatus getStatus() {
        return status;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
