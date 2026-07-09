package com.minipay.wallet;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import com.minipay.user.UserRepository;
import com.minipay.user.User;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    public Wallet createWalletForUser(Long userId) {
        User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if(walletRepository.existsByUserId(userId)) {
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
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public Wallet deposit(Long walletId, BigDecimal amount) {
        Wallet wallet = walletRepository.findById(walletId)
                        .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
        
        if(amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        wallet.deposit(amount);
        return walletRepository.save(wallet);
    }
}
