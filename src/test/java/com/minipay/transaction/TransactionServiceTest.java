package com.minipay.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.minipay.common.exception.TransactionNotFoundException;
import com.minipay.common.exception.WalletNotFoundException;
import com.minipay.transaction.dto.TransactionResponse;
import com.minipay.wallet.Wallet;
import com.minipay.wallet.WalletRepository;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void historyShouldRejectInvalidPageBeforeAccessingRepositories(int page, int size) {
        assertThrows(com.minipay.common.exception.InvalidPageRequestException.class,
                () -> transactionService.getTransactionsByWalletId(1L, null, null, page, size, null, null));
        org.mockito.Mockito.verifyNoInteractions(walletRepository, transactionRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 100})
    void historyShouldAcceptPageSizeBoundaries(int size) {
        var pageable = PageRequest.of(0, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        when(walletRepository.existsById(1L)).thenReturn(true);
        when(transactionRepository.findHistory(1L, null, null, pageable, null, null))
                .thenReturn(Page.empty(pageable));
        assertThat(transactionService.getTransactionsByWalletId(1L, null, null, 0, size, null, null).getSize())
                .isEqualTo(size);
    }

    @Test
    void historyShouldRejectReversedDatesBeforeQueryingTransactions() {
        var from = java.time.LocalDateTime.of(2026, 9, 28, 12, 0);
        when(walletRepository.existsById(1L)).thenReturn(true);

        assertThrows(com.minipay.common.exception.InvalidDateRangeException.class,
                () -> transactionService.getTransactionsByWalletId(1L, null, null, 0, 20,
                        from, from.minusHours(1)));

        org.mockito.Mockito.verifyNoInteractions(transactionRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"true,true", "true,false", "false,true", "false,false"})
    void historyShouldPassOptionalDates(boolean hasFrom, boolean hasTo) {
        var boundary = java.time.LocalDateTime.of(2026, 9, 28, 12, 0);
        var from = hasFrom ? boundary : null;
        var to = hasTo ? boundary : null;
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        when(walletRepository.existsById(1L)).thenReturn(true);
        when(transactionRepository.findHistory(1L, null, null, pageable, from, to))
                .thenReturn(Page.empty(pageable));

        assertThat(transactionService.getTransactionsByWalletId(1L, null, null, 0, 20, from, to)
                .getContent()).isEmpty();
        verify(transactionRepository).findHistory(1L, null, null, pageable, from, to);
    }

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
        when(transactionRepository.findHistory(1L, null, null, PageRequest.of(1, 3, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null))
                .thenReturn(new PageImpl<>(transactions, PageRequest.of(1, 3, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), transactions.isEmpty() ? 0 : 8));

        Page<TransactionResponse> result = transactionService.getTransactionsByWalletId(1L, null, null, 1, 3, null, null);

        assertEquals(3, result.getNumberOfElements());
        assertEquals(1, result.getNumber());
        assertEquals(3, result.getSize());
        assertEquals(8, result.getTotalElements());
        assertEquals(3, result.getTotalPages());

        assertEquals(transaction1.getType(), result.getContent().get(0).getType());
        assertEquals(transaction1.getStatus(), result.getContent().get(0).getStatus());
        assertThat(result.getContent().get(0).getAmount())
                .isEqualByComparingTo(transaction1.getAmount());

        assertEquals(transaction2.getType(), result.getContent().get(1).getType());
        assertEquals(transaction2.getStatus(), result.getContent().get(1).getStatus());
        assertThat(result.getContent().get(1).getAmount())
                .isEqualByComparingTo(transaction2.getAmount());

        assertEquals(transaction3.getType(), result.getContent().get(2).getType());
        assertEquals(transaction3.getStatus(), result.getContent().get(2).getStatus());
        assertThat(result.getContent().get(2).getAmount())
                .isEqualByComparingTo(transaction3.getAmount());

        verify(walletRepository).existsById(1L);
        verify(transactionRepository)
                .findHistory(1L, null, null, PageRequest.of(1, 3, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);
    }

    @Test
    void getTransactionsByWalletIdShouldReturnEmptyTransactions() {

        List<Transaction> transactions = List.of();

        when(walletRepository.existsById(1L))
                .thenReturn(true);
        when(transactionRepository
                .findHistory(1L, null, null, PageRequest.of(1, 3, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null))
                .thenReturn(new PageImpl<>(transactions, PageRequest.of(1, 3, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), transactions.isEmpty() ? 0 : 8));

        Page<TransactionResponse> result = transactionService.getTransactionsByWalletId(1L, null, null, 1, 3, null, null);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getTotalElements());
        verify(transactionRepository)
                .findHistory(1L, null, null, PageRequest.of(1, 3, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);
        verify(walletRepository).existsById(1L);
    }

    @Test
    void getTransactionsByWalletIdShouldThrowWhenWalletNotFound() {

        when(walletRepository.existsById(999L))
                .thenReturn(false);

        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class,
                () -> transactionService.getTransactionsByWalletId(999L, null, null, 1, 3, null, null));

        assertEquals("Wallet not found: 999", exception.getMessage());
        verify(walletRepository).existsById(999L);
        verify(transactionRepository, never())
                .findHistory(999L, null, null, PageRequest.of(1, 3, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);
    }

    @Test
    void getTransacrionByIdShouldReturnTransaction() {
        Wallet wallet = new Wallet();

        Transaction transaction = Transaction.deposit(wallet, new BigDecimal("300.00"));

        when(transactionRepository.findById(1L))
            .thenReturn(Optional.of(transaction));

        Transaction result = transactionService.getTransactionById(1L);

        assertSame(transaction, result);

        assertEquals(transaction.getType(), result.getType());
        assertEquals(transaction.getStatus(), result.getStatus());
        assertThat(result.getAmount())
                .isEqualByComparingTo(transaction.getAmount());

        verify(transactionRepository).findById(1L);
    }

    @Test
    void historyShouldPassFiltersAndPreservePageMetadata() {
        PageRequest pageable = PageRequest.of(1, 2,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Transaction transfer = Transaction.transfer(new Wallet(), new Wallet(), new BigDecimal("25.00"));
        when(walletRepository.existsById(1L)).thenReturn(true);
        when(transactionRepository.findHistory(1L, TransactionType.TRANSFER, TransactionStatus.SUCCESS, pageable, null, null))
                .thenReturn(new PageImpl<>(List.of(transfer), pageable, 3));

        Page<TransactionResponse> result = transactionService.getTransactionsByWalletId(
                1L, TransactionType.TRANSFER, TransactionStatus.SUCCESS, 1, 2, null, null);

        assertThat(result.getContent()).extracting(TransactionResponse::getType)
                .containsExactly(TransactionType.TRANSFER);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getTotalPages()).isEqualTo(2);
        verify(transactionRepository).findHistory(1L, TransactionType.TRANSFER, TransactionStatus.SUCCESS, pageable, null, null);
    }

    @Test
    void getTransacrionByIdShouldThrowWhenTransactionNotFound() {
        when(transactionRepository.findById(999L))
            .thenReturn(Optional.empty());

        TransactionNotFoundException exception = assertThrows(TransactionNotFoundException.class,
            () -> transactionService.getTransactionById(999L));

            assertEquals("Transaction not found: 999", exception.getMessage());
            verify(transactionRepository).findById(999L);
    }
}
