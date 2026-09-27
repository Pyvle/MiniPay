package com.minipay.wallet;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.minipay.user.UserRepository;
import com.minipay.wallet.dto.WalletResponse;

import jakarta.transaction.Transactional;

import com.minipay.user.User;
import com.minipay.transaction.TransactionRepository;
import com.minipay.transaction.TransactionType;
import com.minipay.common.exception.SelfTransferException;
import com.minipay.common.exception.UserNotFoundException;
import com.minipay.common.exception.WalletAlreadyExistsException;
import com.minipay.common.exception.WalletNotFoundException;
import com.minipay.common.validation.AmountValidator;
import com.minipay.idempotency.IdempotencyResponse;
import com.minipay.idempotency.IdempotencyService;
import com.minipay.transaction.Transaction;

@Service
public class WalletService {
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository,
            TransactionRepository transactionRepository,
            IdempotencyService idempotencyService) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
    }

    public Wallet createWalletForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (walletRepository.existsByUserId(userId)) {
            throw new WalletAlreadyExistsException(userId);
        }

        Wallet wallet = new Wallet(user);
        return walletRepository.save(wallet);
    }

    public Wallet getWalletById(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    public Wallet getWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> WalletNotFoundException.forUserId(userId));
    }

    @Transactional
    public IdempotencyResponse deposit(Long walletId, BigDecimal amount, UUID idempotencyKey) {
        AmountValidator.validateAmount(amount);

        return idempotencyService.execute(idempotencyKey,
                TransactionType.DEPOSIT,
                walletId,
                null,
                amount,
                () -> {
                    Wallet wallet = getWalletForUpdate(walletId);

                    wallet.deposit(amount);
                    walletRepository.save(wallet);
                    Transaction transaction = Transaction.deposit(wallet, amount);
                    transactionRepository.save(transaction);
                    return new WalletResponse(wallet);
                });
    }

    @Transactional
    public IdempotencyResponse withdraw(Long walletId, BigDecimal amount, UUID idempotencyKey) {
        AmountValidator.validateAmount(amount);

        return idempotencyService.execute(idempotencyKey,
                TransactionType.WITHDRAWAL,
                walletId,
                null,
                amount,
                () -> {
                    Wallet wallet = getWalletForUpdate(walletId);

                    wallet.withdraw(amount);
                    walletRepository.save(wallet);
                    Transaction transaction = Transaction.withdraw(wallet, amount);
                    transactionRepository.save(transaction);
                    return new WalletResponse(wallet);
                });
    }

    @Transactional
    public IdempotencyResponse transfer(Long fromWalletId, Long toWalletId, BigDecimal amount, UUID idempotencyKey) {
        if (fromWalletId.equals(toWalletId)) {
            throw new SelfTransferException(fromWalletId);
        }
        AmountValidator.validateAmount(amount);

        return idempotencyService.execute(idempotencyKey,
                TransactionType.TRANSFER,
                fromWalletId,
                toWalletId,
                amount,
                () -> {
                    Long firstId = Math.min(fromWalletId, toWalletId);
                    Long secondId = Math.max(fromWalletId, toWalletId);
                    Wallet firstWallet = getWalletForUpdate(firstId);
                    Wallet secondWallet = getWalletForUpdate(secondId);

                    Wallet fromWallet = fromWalletId.equals(firstId)
                            ? firstWallet
                            : secondWallet;
                    Wallet toWallet = toWalletId.equals(firstId)
                            ? firstWallet
                            : secondWallet;

                    fromWallet.withdraw(amount);
                    toWallet.deposit(amount);
                    walletRepository.save(fromWallet);
                    walletRepository.save(toWallet);
                    Transaction transaction = Transaction.transfer(fromWallet, toWallet, amount);
                    transactionRepository.save(transaction);
                    return new WalletResponse(fromWallet);
                });
    }

    private Wallet getWalletForUpdate(Long walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}
