package com.minipay.transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

import com.minipay.support.PostgresTestConfiguration;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.wallet.Wallet;
import com.minipay.wallet.WalletRepository;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class TransactionRepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionRepository transactionRepository;

    private Wallet savedWallet1;
    private Wallet savedWallet2;
    private Wallet savedWallet3;

    @BeforeEach
    void setUp() {
        User user1 = new User("Ivan", "ivan@example.com");
        User user2 = new User("Anna", "anna@example.com");
        User user3 = new User("Sam", "sam@example.com");

        userRepository.saveAndFlush(user1);
        userRepository.saveAndFlush(user2);
        userRepository.saveAndFlush(user3);

        Wallet wallet1 = new Wallet(user1);
        Wallet wallet2 = new Wallet(user2);
        Wallet wallet3 = new Wallet(user3);

        savedWallet1 = walletRepository.saveAndFlush(wallet1);
        savedWallet2 = walletRepository.saveAndFlush(wallet2);
        savedWallet3 = walletRepository.saveAndFlush(wallet3);
    }

    @Test
    void findAllShouldReturnIncomingAndOutgoingTransactions() {
        Transaction deposit = transactionRepository.saveAndFlush(
                Transaction.deposit(savedWallet1, new BigDecimal("100.00")));

        Transaction withdrawal = transactionRepository.saveAndFlush(
                Transaction.withdraw(savedWallet1, new BigDecimal("20.00")));

        Transaction outgoingTransfer = transactionRepository.saveAndFlush(
                Transaction.transfer(
                        savedWallet1,
                        savedWallet2,
                        new BigDecimal("30.00")));

        Transaction incomingTransfer = transactionRepository.saveAndFlush(
                Transaction.transfer(
                        savedWallet3,
                        savedWallet1,
                        new BigDecimal("40.00")));

        Transaction unrelatedTransfer = transactionRepository.saveAndFlush(
                Transaction.transfer(
                        savedWallet2,
                        savedWallet3,
                        new BigDecimal("10.00")));

        List<Transaction> result = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        savedWallet1.getId(),
                        savedWallet1.getId());

        assertThat(result).hasSize(4);

        assertThat(result)
                .extracting(Transaction::getId)
                .containsExactlyInAnyOrder(
                        deposit.getId(),
                        withdrawal.getId(),
                        outgoingTransfer.getId(),
                        incomingTransfer.getId())
                .doesNotContain(unrelatedTransfer.getId());
    }

    @Test
    void findAllShouldNotReturnTransactionsOfOtherWallets() {
        transactionRepository.saveAndFlush(
                Transaction.transfer(
                        savedWallet2,
                        savedWallet3,
                        new BigDecimal("50.00")));

        List<Transaction> result = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        savedWallet1.getId(),
                        savedWallet1.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findAllShouldReturnTransactionsOrderedByCreatedAtDescending() {
        LocalDateTime now = LocalDateTime.now().withNano(0);

        Transaction oldestTransaction = Transaction.deposit(
                savedWallet1,
                new BigDecimal("10.00"));

        Transaction middleTransaction = Transaction.withdraw(
                savedWallet1,
                new BigDecimal("20.00"));

        Transaction newestTransaction = Transaction.transfer(
                savedWallet1,
                savedWallet2,
                new BigDecimal("30.00"));

        ReflectionTestUtils.setField(
                oldestTransaction,
                "createdAt",
                now.minusDays(2));

        ReflectionTestUtils.setField(
                middleTransaction,
                "createdAt",
                now.minusDays(1));

        ReflectionTestUtils.setField(
                newestTransaction,
                "createdAt",
                now);

        transactionRepository.saveAllAndFlush(
                List.of(
                        middleTransaction,
                        newestTransaction,
                        oldestTransaction));

        List<Transaction> result = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        savedWallet1.getId(),
                        savedWallet1.getId());

        assertThat(result).hasSize(3);

        assertThat(result)
                .extracting(Transaction::getId)
                .containsExactly(
                        newestTransaction.getId(),
                        middleTransaction.getId(),
                        oldestTransaction.getId());

        assertThat(result.get(0).getCreatedAt())
                .isAfter(result.get(1).getCreatedAt());

        assertThat(result.get(1).getCreatedAt())
                .isAfter(result.get(2).getCreatedAt());
    }

    @Test
    void findAllShouldReturnEmptyListWhenTransactionsDoNotExist() {
        List<Transaction> result = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        savedWallet1.getId(),
                        savedWallet1.getId());

        assertThat(result).isEmpty();
    }
}
