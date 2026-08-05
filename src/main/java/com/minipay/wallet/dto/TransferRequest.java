package com.minipay.wallet.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class TransferRequest {
    @NotNull
    private Long toWalletId;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    public Long getToWalletId() {
        return toWalletId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setToWalletId(Long toWalletId) {
        this.toWalletId = toWalletId;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}