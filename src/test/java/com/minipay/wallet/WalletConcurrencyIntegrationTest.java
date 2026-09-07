package com.minipay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.minipay.common.exception.InsufficientBalanceException;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.transaction.Transaction;
import com.minipay.transaction.TransactionRepository;
import com.minipay.transaction.TransactionType;
import com.minipay.user.User;
import com.minipay.user.UserRepository;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(PostgresTestConfiguration.class)
class WalletConcurrencyIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Wallet wallet;
    private Wallet secondWallet;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        User user = userRepository.saveAndFlush(
                new User("Ivan", "ivan-" + UUID.randomUUID() + "@example.com"));

        wallet = new Wallet(user);
        wallet.deposit(new BigDecimal("100.00"));
        walletRepository.saveAndFlush(wallet);

        User secondUser = userRepository.saveAndFlush(
                new User("Anna", "anna-" + UUID.randomUUID() + "@example.com"));

        secondWallet = new Wallet(secondUser);
        secondWallet.deposit(new BigDecimal("100.00"));
        walletRepository.saveAndFlush(secondWallet);
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            deleteWalletTestData(wallet);
            deleteWalletTestData(secondWallet);
        });
    }

    private void deleteWalletTestData(Wallet testWallet) {
        if (testWallet != null && testWallet.getId() != null) {
            Long walletId = testWallet.getId();
            Long userId = testWallet.getUser().getId();
            List<Transaction> transactions = transactionRepository
                    .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(walletId, walletId);

            transactionRepository.deleteAll(transactions);
            transactionRepository.flush();

            walletRepository.deleteById(walletId);
            walletRepository.flush();
            userRepository.deleteById(userId);
        }
    }

    @Test
    void concurrentDepositsShouldPreserveBothAmounts() throws Exception {
        Long walletId = wallet.getId();
        CountDownLatch ready = new CountDownLatch(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> firstTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                walletService.deposit(walletId, new BigDecimal("20.00"));
            });

            Future<?> secondTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                walletService.deposit(walletId, new BigDecimal("30.00"));
            });

            firstTask.get(10, TimeUnit.SECONDS);
            secondTask.get(10, TimeUnit.SECONDS);
        }

        Wallet persistedWallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(persistedWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("150.00"));
        List<Transaction> transactions = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                        walletId, walletId);

        assertThat(transactions)
                .hasSize(2)
                .extracting(Transaction::getAmount)
                .containsExactlyInAnyOrder(
                        new BigDecimal("20.00"),
                        new BigDecimal("30.00"));
    }

    @Test
    void concurrentWithdrawalsShouldNotOverdrawWallet() throws Exception {
        Long walletId = wallet.getId();
        CountDownLatch ready = new CountDownLatch(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);

                try {
                    walletService.withdraw(walletId, new BigDecimal("80.00"));
                    return true;
                } catch (InsufficientBalanceException e) {
                    return false;
                }
            });

            Future<Boolean> secondTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);

                try {
                    walletService.withdraw(walletId, new BigDecimal("80.00"));
                    return true;
                } catch (InsufficientBalanceException e) {
                    return false;
                }
            });

            boolean firstSucceeded = firstTask.get(10, TimeUnit.SECONDS);
            boolean secondSucceeded = secondTask.get(10, TimeUnit.SECONDS);

            assertTrue(firstSucceeded ^ secondSucceeded);

            Wallet persistedWallet = walletRepository.findById(walletId).orElseThrow();
            assertThat(persistedWallet.getBalance())
                    .isEqualByComparingTo(new BigDecimal("20.00"));
            List<Transaction> transactions = transactionRepository
                    .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(
                            walletId, walletId);

            assertThat(transactions).hasSize(1);

            Transaction transaction = transactions.get(0);

            assertThat(transaction.getAmount())
                    .isEqualByComparingTo(new BigDecimal("80.00"));

            assertThat(transaction.getType())
                    .isEqualTo(TransactionType.WITHDRAWAL);
        }
    }

    @Test
    void concurrentOppositeTransfersShouldPreserveBalances() throws Exception {
        Long walletId = wallet.getId();
        Long secondWalletId = secondWallet.getId();
        CountDownLatch ready = new CountDownLatch(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> firstTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                walletService.transfer(walletId, secondWalletId, new BigDecimal("30.00"));
            });

            Future<?> secondTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                walletService.transfer(secondWalletId, walletId, new BigDecimal("20.00"));
            });

            firstTask.get(10, TimeUnit.SECONDS);
            secondTask.get(10, TimeUnit.SECONDS);
        }

        Wallet persistedWallet = walletRepository.findById(walletId).orElseThrow();
        Wallet persistedSecondWallet = walletRepository.findById(secondWalletId).orElseThrow();

        assertThat(persistedWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(persistedSecondWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("110.00"));

        List<Transaction> transactions = transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(walletId, walletId);

        assertThat(transactions)
                .hasSize(2)
                .allSatisfy(transaction -> assertThat(transaction.getType()).isEqualTo(TransactionType.TRANSFER))
                .satisfiesExactlyInAnyOrder(
                        transaction -> {
                            assertThat(transaction.getAmount())
                                    .isEqualByComparingTo(new BigDecimal("30.00"));
                            assertThat(transaction.getFromWallet().getId()).isEqualTo(walletId);
                            assertThat(transaction.getToWallet().getId()).isEqualTo(secondWalletId);
                        },
                        transaction -> {
                            assertThat(transaction.getAmount())
                                    .isEqualByComparingTo(new BigDecimal("20.00"));
                            assertThat(transaction.getFromWallet().getId()).isEqualTo(secondWalletId);
                            assertThat(transaction.getToWallet().getId()).isEqualTo(walletId);
                        });
    }

    @Test
    void findByIdForUpdateShouldFailWhileWalletIsLocked() {
        Long walletId = wallet.getId();
        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            transactionTemplate.executeWithoutResult(status -> {
                walletRepository.findByIdForUpdate(walletId).orElseThrow();

                Future<?> task = executor.submit(() -> {
                    transactionTemplate.executeWithoutResult(workerStatus -> {

                        walletRepository.findByIdForUpdate(walletId).orElseThrow();
                    });
                });

                ExecutionException exception = assertThrows(
                        ExecutionException.class,
                        () -> task.get(10, TimeUnit.SECONDS));

                assertThat(exception.getCause())
                        .isInstanceOf(PessimisticLockingFailureException.class);

                assertThat(exception)
                        .rootCause()
                        .isInstanceOfSatisfying(SQLException.class, sqlException -> {
                            assertThat(sqlException.getSQLState())
                                    .isEqualTo("55P03");
                        });
            });

        }
    }

    @Test
    void transferShouldRollbackWhenSecondWalletLockTimesOut() {
        Long firstId = Math.min(wallet.getId(), secondWallet.getId());
        Long secondId = Math.max(wallet.getId(), secondWallet.getId());

        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            transactionTemplate.executeWithoutResult(status -> {
                // Keep the second wallet locked until both worker checks finish.
                walletRepository.findByIdForUpdate(secondId).orElseThrow();

                Future<?> transferTask = executor.submit(() -> {
                    walletService.transfer(firstId, secondId, new BigDecimal("20.00"));
                });

                ExecutionException exception = assertThrows(
                        ExecutionException.class,
                        () -> transferTask.get(10, TimeUnit.SECONDS));

                assertThat(exception.getCause())
                        .isInstanceOf(PessimisticLockingFailureException.class);
                assertThat(exception)
                        .rootCause()
                        .isInstanceOfSatisfying(SQLException.class, sqlException -> {
                            assertThat(sqlException.getSQLState()).isEqualTo("55P03");
                        });

                // The failed transfer must release its lock on the first wallet.
                Future<?> lockCheck = executor.submit(() -> {
                    transactionTemplate.executeWithoutResult(checkStatus -> {
                        walletRepository.findByIdForUpdate(firstId).orElseThrow();
                    });
                });

                assertDoesNotThrow(() -> lockCheck.get(10, TimeUnit.SECONDS));
            });
        }

        Wallet persistedFirstWallet = walletRepository.findById(firstId).orElseThrow();
        Wallet persistedSecondWallet = walletRepository.findById(secondId).orElseThrow();

        assertThat(persistedFirstWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(persistedSecondWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));

        assertThat(transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(firstId, firstId))
                .isEmpty();
        assertThat(transactionRepository
                .findAllByFromWalletIdOrToWalletIdOrderByCreatedAtDesc(secondId, secondId))
                .isEmpty();
    }

    private void awaitBothTasksReady(CountDownLatch ready) {
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Вторая задача не дошла до ожидания");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ожидание прервано", e);
        }
    }
}
