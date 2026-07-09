package com.minipay.wallet.dto;

import jakarta.validation.constraints.NotNull;

public class CreateWalletRequest {
    @NotNull
    private Long userId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

}
