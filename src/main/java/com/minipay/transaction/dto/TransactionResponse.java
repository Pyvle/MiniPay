package com.minipay.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

import com.minipay.transaction.TransactionStatus;
import com.minipay.transaction.TransactionType;
import com.minipay.transaction.Transaction;

public class TransactionResponse {
    private Long id;
    @Schema(description = "ID кошелька отправителя; null при пополнении", nullable = true, example = "25")
    private Long fromWalletId;
    @Schema(description = "ID кошелька получателя; null при снятии", nullable = true, example = "26")
    private Long toWalletId;
    @Schema(description = "Сумма операции без долей копейки", minimum = "0.01", maximum = "99999999999999999.99", multipleOf = 0.01, example = "150.00")
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private LocalDateTime createdAt;

    public TransactionResponse(Transaction transaction) {
        this.id = transaction.getId();
        this.fromWalletId = transaction.getFromWallet() == null
                ? null
                : transaction.getFromWallet().getId();
        this.toWalletId = transaction.getToWallet() == null
                ? null
                : transaction.getToWallet().getId();
        this.amount = transaction.getAmount();
        this.type = transaction.getType();
        this.status = transaction.getStatus();
        this.createdAt = transaction.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public Long getFromWalletId() {
        return fromWalletId;
    }

    public Long getToWalletId() {
        return toWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
