package com.minipay.transaction;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.minipay.common.exception.InvalidDateRangeException;
import com.minipay.common.exception.InvalidPageRequestException;
import com.minipay.common.exception.TransactionNotFoundException;
import com.minipay.common.exception.WalletNotFoundException;
import com.minipay.transaction.dto.TransactionResponse;
import com.minipay.wallet.WalletRepository;

@Service
public class TransactionService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository, WalletRepository walletRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
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

    public Transaction getTransactionById(Long id) {

        return transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

}
