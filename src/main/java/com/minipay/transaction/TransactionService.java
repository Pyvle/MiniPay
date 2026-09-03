package com.minipay.transaction;

import java.util.List;

import org.springframework.stereotype.Service;

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

    public List<TransactionResponse> getTransactionsByWalletId(Long walletId) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }

        List<Transaction> transactions = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(walletId, walletId);

        return transactions.stream()
                .map(TransactionResponse::new)
                .toList();
    }

}
