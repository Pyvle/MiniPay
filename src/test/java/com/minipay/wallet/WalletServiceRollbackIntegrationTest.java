package com.minipay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.minipay.transaction.Transaction;
import com.minipay.transaction.TransactionRepository;
import com.minipay.user.User;
import com.minipay.user.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
class WalletServiceRollbackIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @MockitoBean
    private TransactionRepository transactionRepository;

    @Test
    void transferShouldRollbackWhenTransactionSaveFails() {
        User savedToUser = userRepository.saveAndFlush(
                new User("Obama", "obama@example.com"));
        Wallet toWallet = walletRepository.saveAndFlush(
                new Wallet(savedToUser));
        User savedFromUser = userRepository.saveAndFlush(
                new User("Ivan", "ivan@example.com"));
        Wallet fromWallet = new Wallet(savedFromUser);
        fromWallet.deposit(new BigDecimal("300.00"));
        fromWallet = walletRepository.saveAndFlush(fromWallet);

        Long fromWalletId = fromWallet.getId();
        Long toWalletId = toWallet.getId();

        when(transactionRepository.save(any(Transaction.class)))
                .thenThrow(new RuntimeException("Database error"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> walletService.transfer(
                        fromWalletId,
                        toWalletId,
                        new BigDecimal("100.00")));

        assertEquals("Database error", exception.getMessage());

        Wallet persistedFromWallet = walletRepository.findById(fromWalletId).orElseThrow();
        Wallet persistedToWallet = walletRepository.findById(toWalletId).orElseThrow();

        assertThat(persistedFromWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("300.00"));
        assertThat(persistedToWallet.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @AfterEach
    void cleanUp() {
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
