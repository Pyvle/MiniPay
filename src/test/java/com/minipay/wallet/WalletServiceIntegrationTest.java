package com.minipay.wallet;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.minipay.support.PostgresTestConfiguration;
import com.minipay.transaction.Transaction;
import com.minipay.transaction.TransactionRepository;
import com.minipay.transaction.TransactionStatus;
import com.minipay.transaction.TransactionType;
import com.minipay.user.User;
import com.minipay.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Transactional
@Import(PostgresTestConfiguration.class)
class WalletServiceIntegrationTest {


    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(
                new User("Ivan", "ivan@example.com"));

        wallet = walletRepository.saveAndFlush(new Wallet(user));
    }

    @Test
    void depositShouldPersistBalanceAndTransaction() {
        walletService.deposit(wallet.getId(), new BigDecimal("100.00"));

        entityManager.flush();
        entityManager.clear();

        Wallet persistedWallet = walletRepository.findById(wallet.getId()).orElseThrow();

        List<Transaction> transactions = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        wallet.getId(),
                        wallet.getId());

        assertThat(persistedWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(transactions).hasSize(1);
        assertEquals(TransactionType.DEPOSIT,
                transactions.get(0).getType());
        assertEquals(TransactionStatus.SUCCESS,
                transactions.get(0).getStatus());
        assertThat(transactions.get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertNull(transactions.get(0).getFromWallet());
        assertEquals(persistedWallet.getId(),
                transactions.get(0).getToWallet().getId());
    }

    @Test
    void transferShouldPersistBalancesAndTransaction() {
        wallet.deposit(new BigDecimal("300.00"));
        walletRepository.saveAndFlush(wallet);

        User savedToUser = userRepository.saveAndFlush(
                new User("Obama", "obama@example.com"));
        Wallet toWallet = walletRepository.saveAndFlush(
                new Wallet(savedToUser));

        Long fromWalletId = wallet.getId();
        Long toWalletId = toWallet.getId();

        walletService.transfer(fromWalletId, toWalletId, new BigDecimal("100.00"));

        entityManager.flush();
        entityManager.clear();

        Wallet persistedFromWallet = walletRepository.findById(fromWalletId).orElseThrow();
        Wallet persistedToWallet = walletRepository.findById(toWalletId).orElseThrow();

        assertThat(persistedFromWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(persistedToWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));

        List<Transaction> transactions = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        fromWalletId, fromWalletId);

        assertThat(transactions).hasSize(1);
        assertEquals(TransactionType.TRANSFER, transactions.get(0).getType());
        assertEquals(TransactionStatus.SUCCESS, transactions.get(0).getStatus());
        assertThat(transactions.get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertEquals(persistedFromWallet.getId(), transactions.get(0).getFromWallet().getId());
        assertEquals(persistedToWallet.getId(), transactions.get(0).getToWallet().getId());
    }
}
