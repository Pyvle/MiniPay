package com.minipay.wallet.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public class TransferRequest {
    @NotNull
    @Schema(description = "ID существующего кошелька получателя, отличного от кошелька отправителя", example = "26",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long toWalletId;

    @NotNull
    @DecimalMin(value = "0.01")
    @Schema(description = "Сумма перевода без долей копейки; округление не выполняется", minimum = "0.01",
            maximum = "99999999999999999.99", multipleOf = 0.01, example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
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
