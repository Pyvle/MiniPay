package com.minipay.wallet;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.minipay.user.UserRepository;

import jakarta.transaction.Transactional;

import com.minipay.user.User;
import com.minipay.transaction.TransactionRepository;
import com.minipay.transaction.Transaction;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository,
            TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public Wallet createWalletForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (walletRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("Wallet with this user already exists");
        }

        Wallet wallet = new Wallet(user);
        return walletRepository.save(wallet);
    }

    public Wallet getWalletById(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }

    public Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }

    @Transactional
    public Wallet deposit(Long walletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Wallet wallet = getWalletById(walletId);

        wallet.deposit(amount);
        walletRepository.save(wallet);
        Transaction transaction = Transaction.deposit(wallet, amount);
        transactionRepository.save(transaction);
        return wallet;
    }

    @Transactional
    public Wallet withdraw(Long walletId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Wallet wallet = getWalletById(walletId);

        wallet.withdraw(amount);
        walletRepository.save(wallet);
        Transaction transaction = Transaction.withdraw(wallet, amount);
        transactionRepository.save(transaction);
        return wallet;
    }

    @Transactional
    public Wallet transfer(Long fromWalletId, Long toWalletId, BigDecimal amount) {
        if (fromWalletId.equals(toWalletId)) {
            throw new IllegalArgumentException("Source and destination wallets must be different");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Wallet fromWallet = getWalletById(fromWalletId);
        Wallet toWallet = getWalletById(toWalletId);

        fromWallet.withdraw(amount);
        toWallet.deposit(amount);
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);
        Transaction transaction = Transaction.transfer(fromWallet, toWallet, amount);
        transactionRepository.save(transaction);
        return fromWallet;
    }

}
