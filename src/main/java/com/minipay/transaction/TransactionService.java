package com.minipay.transaction;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipay.common.exception.InvalidDateRangeException;
import com.minipay.common.exception.InvalidPageRequestException;
import com.minipay.common.exception.TransactionNotFoundException;
import com.minipay.common.exception.WalletNotFoundException;
import com.minipay.permission.PermissionCode;
import com.minipay.permission.PermissionService;
import com.minipay.transaction.dto.TransactionResponse;
import com.minipay.wallet.Wallet;
import com.minipay.wallet.WalletRepository;

@Service
public class TransactionService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final PermissionService permissionService;

    public TransactionService(TransactionRepository transactionRepository, WalletRepository walletRepository,
            PermissionService permissionService) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.permissionService = permissionService;
    }

    public Page<TransactionResponse> getTransactionsByWalletId(Long walletId,
            TransactionType type, TransactionStatus status, int page, int size,
            LocalDateTime from, LocalDateTime to) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPageRequestException();
        }
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Transaction> transactions = transactionRepository
                .findHistory(walletId, type, status, pageable, from, to);

        return transactions.map(TransactionResponse::new);
    }

    @Transactional(readOnly = true)
    public Transaction getTransactionById(Long actorId, Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        if (!ownsWallet(actorId, transaction.getFromWallet())
                && !ownsWallet(actorId, transaction.getToWallet())
                && !permissionService.hasPermission(actorId, PermissionCode.TRANSACTION_READ_ANY)) {
            throw new TransactionNotFoundException(id);
        }
        return transaction;
    }

    private boolean ownsWallet(Long actorId, Wallet wallet) {
        return wallet != null && actorId.equals(wallet.getUser().getId());
    }

}
