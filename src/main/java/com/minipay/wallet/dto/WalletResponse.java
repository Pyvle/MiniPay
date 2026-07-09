package com.minipay.wallet.dto;

import java.time.LocalDateTime;

import com.minipay.wallet.Wallet;

import java.math.BigDecimal;

public class WalletResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private BigDecimal balance;
    private LocalDateTime createdAt;

    public WalletResponse(Wallet wallet) {
        this.id = wallet.getId();
        this.userId = wallet.getUser().getId();
        this.userName = wallet.getUser().getName();
        this.userEmail = wallet.getUser().getEmail();
        this.balance = wallet.getBalance();
        this.createdAt = wallet.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
