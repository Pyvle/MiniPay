package com.minipay.wallet;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import com.minipay.permission.PermissionCode;
import com.minipay.permission.PermissionService;

import com.minipay.user.UserRepository;

import jakarta.transaction.Transactional;

import com.minipay.user.User;
import com.minipay.user.UserState;
import com.minipay.transaction.TransactionRepository;
import com.minipay.transaction.TransactionType;
import com.minipay.transaction.dto.TransactionResponse;
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
    private final PermissionService permissionService;

    public WalletService(WalletRepository walletRepository, UserRepository userRepository,
            TransactionRepository transactionRepository,
            IdempotencyService idempotencyService, PermissionService permissionService) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
        this.permissionService = permissionService;
    }

    public Wallet createWalletForUser(Long actorId) {
        User user = userRepository.findById(actorId)
                .orElseThrow(() -> new UserNotFoundException(actorId));

        if (walletRepository.existsByUserId(actorId)) {
            throw new WalletAlreadyExistsException(actorId);
        }

        Wallet wallet = new Wallet(user);
        return walletRepository.save(wallet);
    }

    public Wallet getWalletById(Long actorId, Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        if (!canReadWallet(actorId, wallet.getUser().getId())) {
            throw new WalletNotFoundException(walletId);
        }
        return wallet;
    }

    public Wallet getWalletByUserId(Long actorId, Long userId) {
        if (!canReadWallet(actorId, userId)) {
            throw WalletNotFoundException.forUserId(userId);
        }
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> WalletNotFoundException.forUserId(userId));
    }

    private boolean canReadWallet(Long actorId, Long ownerId) {
        return actorId.equals(ownerId)
                || permissionService.hasPermission(actorId, PermissionCode.WALLET_READ_ANY);
    }

    public void requireHistoryAccess(Long actorId, Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        if (!actorId.equals(wallet.getUser().getId())
                && !permissionService.hasPermission(actorId, PermissionCode.HISTORY_READ_ANY)) {
            throw new WalletNotFoundException(walletId);
        }
    }

    @Transactional
    public IdempotencyResponse depositForUser(Long actorId, BigDecimal amount, UUID key) {
        return deposit(ownWalletId(actorId), amount, key);
    }

    @Transactional
    public IdempotencyResponse withdrawForUser(Long actorId, BigDecimal amount, UUID key) {
        return withdraw(ownWalletId(actorId), amount, key);
    }

    @Transactional
    public IdempotencyResponse transferForUser(Long actorId, Long toWalletId, BigDecimal amount, UUID key) {
        Long fromWalletId = ownWalletId(actorId);
        if (fromWalletId.equals(toWalletId)) {
            throw new SelfTransferException(fromWalletId);
        }
        walletRepository.findOwnerState(toWalletId)
                .filter(state -> state == UserState.ACTIVE)
                .orElseThrow(() -> new WalletNotFoundException(toWalletId));
        return transfer(fromWalletId, toWalletId, amount, key);
    }

    private Long ownWalletId(Long actorId) {
        return walletRepository.findIdByUserId(actorId)
                .orElseThrow(() -> WalletNotFoundException.forUserId(actorId));
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
                    return new TransactionResponse(transaction);
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
                    return new TransactionResponse(transaction);
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
                    return new TransactionResponse(transaction);
                });
    }

    private Wallet getWalletForUpdate(Long walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }
}
