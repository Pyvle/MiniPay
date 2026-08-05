package com.minipay.transaction;

import com.minipay.wallet.Wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "from_wallet_id", nullable = true)
    private Wallet fromWallet;

    @ManyToOne
    @JoinColumn(name = "to_wallet_id", nullable = true)
    private Wallet toWallet;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Transaction() {
    }

    public static Transaction deposit(Wallet toWallet, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.toWallet = toWallet;
        transaction.amount = amount;
        transaction.type = TransactionType.DEPOSIT;
        transaction.status = TransactionStatus.SUCCESS;
        return transaction;
    }

    public static Transaction withdraw(Wallet fromWallet, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.fromWallet = fromWallet;
        transaction.amount = amount;
        transaction.type = TransactionType.WITHDRAWAL;
        transaction.status = TransactionStatus.SUCCESS;
        return transaction;
    }

    public static Transaction transfer(Wallet fromWallet, Wallet toWallet, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.fromWallet = fromWallet;
        transaction.toWallet = toWallet;
        transaction.amount = amount;
        transaction.type = TransactionType.TRANSFER;
        transaction.status = TransactionStatus.SUCCESS;
        return transaction;
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Wallet getFromWallet() {
        return fromWallet;
    }

    public Wallet getToWallet() {
        return toWallet;
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
