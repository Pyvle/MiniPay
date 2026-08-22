package com.minipay.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.minipay.transaction.dto.TransactionResponse;
import com.minipay.wallet.Wallet;
import com.minipay.wallet.WalletRepository;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void getTransactionsByWalletIdShouldReturnTransactions() {
        Wallet wallet = new Wallet();
        Wallet wallet2 = new Wallet();

        Transaction transaction1 = Transaction.deposit(wallet, new BigDecimal("300.00"));
        Transaction transaction2 = Transaction.withdraw(wallet, new BigDecimal("100.00"));
        Transaction transaction3 = Transaction.transfer(wallet, wallet2, new BigDecimal("100.00"));

        List<Transaction> transactions = List.of(transaction1, transaction2, transaction3);

        when(walletRepository.existsById(1L))
                .thenReturn(true);
        when(transactionRepository.findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(1L, 1L))
                .thenReturn(transactions);

        List<TransactionResponse> result = transactionService.getTransactionsByWalletId(1L);

        assertEquals(3, result.size());

        assertEquals(transaction1.getType(), result.get(0).getType());
        assertEquals(transaction1.getStatus(), result.get(0).getStatus());
        assertThat(result.get(0).getAmount())
                .isEqualByComparingTo(transaction1.getAmount());

        assertEquals(transaction2.getType(), result.get(1).getType());
        assertEquals(transaction2.getStatus(), result.get(1).getStatus());
        assertThat(result.get(1).getAmount())
                .isEqualByComparingTo(transaction2.getAmount());

        assertEquals(transaction3.getType(), result.get(2).getType());
        assertEquals(transaction3.getStatus(), result.get(2).getStatus());
        assertThat(result.get(2).getAmount())
                .isEqualByComparingTo(transaction3.getAmount());

        verify(walletRepository).existsById(1L);
        verify(transactionRepository)
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(1L, 1L);
    }

    @Test
    void getTransactionsByWalletIdShouldReturnEmptyTransactions() {

        List<Transaction> transactions = List.of();

        when(walletRepository.existsById(1L))
                .thenReturn(true);
        when(transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(1L, 1L))
                .thenReturn(transactions);

        List<TransactionResponse> result = transactionService.getTransactionsByWalletId(1L);

        assertTrue(result.isEmpty());
        verify(transactionRepository)
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(1L, 1L);
        verify(walletRepository).existsById(1L);
    }

    @Test
    void getTransactionsByWalletIdShouldThrowWhenWalletNotFound() {

        when(walletRepository.existsById(999L))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> transactionService.getTransactionsByWalletId(999L));

        assertEquals("Wallet not found", exception.getMessage());
        verify(walletRepository).existsById(999L);
        verify(transactionRepository, never())
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(999L, 999L);
    }
}
