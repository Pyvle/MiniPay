package com.minipay.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.minipay.common.exception.BalanceLimitExceededException;
import com.minipay.common.exception.InsufficientBalanceException;
import com.minipay.common.validation.AmountValidator;
import com.minipay.user.User;

@Entity
@Table(name = "wallets")
public class Wallet {

private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Wallet() {
    }

    public Wallet(User user) {
        this.user = user;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }

        if (this.balance == null) {
            balance = BigDecimal.ZERO;
        }
    }

    public void deposit(BigDecimal amount) {
        AmountValidator.validateAmount(amount);

        BigDecimal newBalance = this.balance.add(amount);
        if(newBalance.compareTo(MAX_BALANCE) > 0) {
            throw new BalanceLimitExceededException();
        }

        this.balance = newBalance;
    }

    public void withdraw(BigDecimal amount) {
        AmountValidator.validateAmount(amount);

        if (balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException();
        }

        this.balance = this.balance.subtract(amount);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
