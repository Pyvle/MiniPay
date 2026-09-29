package com.minipay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import java.sql.SQLException;
import java.util.ArrayList;
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
import com.minipay.common.exception.BalanceLimitExceededException;
import com.minipay.idempotency.IdempotencyRecordRepository;
import com.minipay.idempotency.IdempotencyStatus;
import com.minipay.transaction.TransactionStatus;
import com.minipay.common.exception.IdempotencyConflictException;
import com.minipay.idempotency.IdempotencyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    private final List<UUID> usedKeys = new ArrayList<>();

    private Wallet wallet;
    private Wallet secondWallet;
    private Wallet thirdWallet;

    @Autowired
    private ObjectMapper objectMapper;
    UUID key;

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

        key = newRequestKey();
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            idempotencyRecordRepository.deleteAllById(usedKeys);
            deleteWalletTestData(wallet);
            deleteWalletTestData(secondWallet);
            deleteWalletTestData(thirdWallet);
        });
        assertThat(idempotencyRecordRepository.findAllById(usedKeys)).isEmpty();
    }

    private UUID newRequestKey() {
        UUID requestKey = UUID.randomUUID();
        usedKeys.add(requestKey);
        return requestKey;
    }

    private void deleteWalletTestData(Wallet testWallet) {
        if (testWallet != null && testWallet.getId() != null) {
            Long walletId = testWallet.getId();
            Long userId = testWallet.getUser().getId();
            List<Transaction> transactions = transactionRepository
                    .findHistory(walletId, null, null, Pageable.unpaged(), null, null).getContent();

            transactionRepository.deleteAll(transactions);
            transactionRepository.flush();

            walletRepository.deleteById(walletId);
            walletRepository.flush();
            userRepository.deleteById(userId);
        }
    }

    @Test
    void repeatedDepositWithSameKeyShouldExecuteOnlyOnce() {
        Long walletId = wallet.getId();
        UUID key = newRequestKey();

        IdempotencyResponse firstResponse = walletService.deposit(walletId, new BigDecimal("20.00"), key);
        IdempotencyResponse repeatedResponse = walletService.deposit(walletId, new BigDecimal("20.00"), key);

        assertThat(repeatedResponse).isEqualTo(firstResponse);

        Wallet persistedWallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(persistedWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("120.00"));

        List<Transaction> transactions = transactionRepository
                .findHistory(walletId, null, null, Pageable.unpaged(), null, null).getContent();

        assertThat(transactions).hasSize(1);
        Transaction transaction = transactions.get(0);
        assertThat(transaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getAmount())
                .isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void concurrentDepositsShouldPreserveBothAmounts() throws Exception {
        Long walletId = wallet.getId();
        UUID firstKey = newRequestKey();
        UUID secondKey = newRequestKey();
        CountDownLatch ready = new CountDownLatch(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> firstTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                walletService.deposit(walletId, new BigDecimal("20.00"), firstKey);
            });

            Future<?> secondTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                walletService.deposit(walletId, new BigDecimal("30.00"), secondKey);
            });

            firstTask.get(10, TimeUnit.SECONDS);
            secondTask.get(10, TimeUnit.SECONDS);
        }

        Wallet persistedWallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(persistedWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("150.00"));
        List<Transaction> transactions = transactionRepository
                .findHistory(walletId, null, null, Pageable.unpaged(), null, null).getContent();

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
        UUID firstKey = newRequestKey();
        UUID secondKey = newRequestKey();
        CountDownLatch ready = new CountDownLatch(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);

                try {
                    walletService.withdraw(walletId, new BigDecimal("80.00"), firstKey);
                    return true;
                } catch (InsufficientBalanceException e) {
                    return false;
                }
            });

            Future<Boolean> secondTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);

                try {
                    walletService.withdraw(walletId, new BigDecimal("80.00"), secondKey);
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
                    .findHistory(walletId, null, null, Pageable.unpaged(), null, null).getContent();

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
        UUID firstKey = newRequestKey();
        UUID secondKey = newRequestKey();
        CountDownLatch ready = new CountDownLatch(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> firstTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                walletService.transfer(walletId, secondWalletId, new BigDecimal("30.00"), firstKey);
            });

            Future<?> secondTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                walletService.transfer(secondWalletId, walletId, new BigDecimal("20.00"), secondKey);
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
                .findHistory(walletId, null, null, Pageable.unpaged(), null, null).getContent();

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
        UUID key = newRequestKey();
        Long firstId = Math.min(wallet.getId(), secondWallet.getId());
        Long secondId = Math.max(wallet.getId(), secondWallet.getId());

        try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
            transactionTemplate.executeWithoutResult(status -> {
                // Keep the second wallet locked until both worker checks finish.
                walletRepository.findByIdForUpdate(secondId).orElseThrow();

                Future<?> transferTask = executor.submit(() -> {
                    walletService.transfer(firstId, secondId, new BigDecimal("20.00"), key);
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
                assertThat(idempotencyRecordRepository.findById(key)).isEmpty();
                assertBalance(wallet, "100.00");
                assertBalance(secondWallet, "100.00");
                assertThat(history(wallet)).isEmpty();
                assertThat(history(secondWallet)).isEmpty();
            });
        }

        Wallet persistedFirstWallet = walletRepository.findById(firstId).orElseThrow();
        Wallet persistedSecondWallet = walletRepository.findById(secondId).orElseThrow();

        assertThat(persistedFirstWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(persistedSecondWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));

        assertThat(transactionRepository
                .findHistory(firstId, null, null, Pageable.unpaged(), null, null).getContent())
                .isEmpty();
        assertThat(transactionRepository
                .findHistory(secondId, null, null, Pageable.unpaged(), null, null).getContent())
                .isEmpty();

        // executeWithoutResult has returned: the transaction holding the lock is finished.
        IdempotencyResponse successfulResponse = walletService.transfer(
                firstId, secondId, new BigDecimal("20.00"), key);
        IdempotencyResponse repeatedResponse = walletService.transfer(
                firstId, secondId, new BigDecimal("20.00"), key);
        assertThat(repeatedResponse).isEqualTo(successfulResponse);
        assertThat(walletRepository.findById(firstId).orElseThrow().getBalance())
                .isEqualByComparingTo("80.00");
        assertThat(walletRepository.findById(secondId).orElseThrow().getBalance())
                .isEqualByComparingTo("120.00");
        assertSingleOperation(wallet, TransactionType.TRANSFER, "20.00");
        assertSingleOperation(secondWallet, TransactionType.TRANSFER, "20.00");
        assertCompletedRequest(key, successfulResponse);
    }

    @Test
    void repeatedWithdrawalWithSameKeyShouldExecuteOnlyOnce() {
        IdempotencyResponse firstResponse = walletService.withdraw(wallet.getId(), new BigDecimal("20.00"), key);
        IdempotencyResponse repeatedResponse = walletService.withdraw(wallet.getId(), new BigDecimal("20.00"), key);

        assertThat(repeatedResponse).isEqualTo(firstResponse);
        assertBalance(wallet, "80.00");
        assertBalance(secondWallet, "100.00");
        assertSingleOperation(wallet, TransactionType.WITHDRAWAL, "20.00");
        assertThat(history(secondWallet)).isEmpty();
    }

    @Test
    void repeatedTransferWithSameKeyShouldExecuteOnlyOnce() {
        IdempotencyResponse firstResponse = walletService.transfer(
                wallet.getId(), secondWallet.getId(), new BigDecimal("20.00"), key);
        IdempotencyResponse repeatedResponse = walletService.transfer(
                wallet.getId(), secondWallet.getId(), new BigDecimal("20.00"), key);

        assertThat(repeatedResponse).isEqualTo(firstResponse);
        assertBalance(wallet, "80.00");
        assertBalance(secondWallet, "120.00");
        assertSingleOperation(wallet, TransactionType.TRANSFER, "20.00");
        assertSingleOperation(secondWallet, TransactionType.TRANSFER, "20.00");
        assertThat(history(secondWallet).get(0).getId()).isEqualTo(history(wallet).get(0).getId());
    }

    @Test
    void sameKeyWithDifferentAmountShouldConflictWithoutChangingState() {
        walletService.deposit(wallet.getId(), new BigDecimal("20.00"), key);
        Long originalTransactionId = history(wallet).get(0).getId();

        assertThrows(IdempotencyConflictException.class,
                () -> walletService.deposit(wallet.getId(), new BigDecimal("50.00"), key));

        assertDepositStateUnchanged(originalTransactionId);
    }

    @Test
    void sameKeyWithDifferentWalletShouldConflictWithoutChangingState() {
        walletService.deposit(wallet.getId(), new BigDecimal("20.00"), key);
        Long originalTransactionId = history(wallet).get(0).getId();

        assertThrows(IdempotencyConflictException.class,
                () -> walletService.deposit(secondWallet.getId(), new BigDecimal("20.00"), key));

        assertDepositStateUnchanged(originalTransactionId);
    }

    @Test
    void sameKeyWithDifferentRecipientShouldConflictWithoutChangingState() {
        User thirdUser = userRepository.saveAndFlush(
                new User("Petr", "petr-" + UUID.randomUUID() + "@example.com"));
        thirdWallet = new Wallet(thirdUser);
        thirdWallet.deposit(new BigDecimal("100.00"));
        walletRepository.saveAndFlush(thirdWallet);

        walletService.transfer(wallet.getId(), secondWallet.getId(), new BigDecimal("20.00"), key);
        Long originalTransactionId = history(wallet).get(0).getId();

        assertThrows(IdempotencyConflictException.class,
                () -> walletService.transfer(wallet.getId(), thirdWallet.getId(), new BigDecimal("20.00"), key));

        assertBalance(wallet, "80.00");
        assertBalance(secondWallet, "120.00");
        assertBalance(thirdWallet, "100.00");
        assertSingleOperation(wallet, TransactionType.TRANSFER, "20.00");
        assertSingleOperation(secondWallet, TransactionType.TRANSFER, "20.00");
        assertThat(history(wallet).get(0).getId()).isEqualTo(originalTransactionId);
        assertThat(history(secondWallet).get(0).getId()).isEqualTo(originalTransactionId);
        assertThat(history(thirdWallet)).isEmpty();
    }

    @Test
    void sameKeyWithDifferentOperationTypeShouldConflictWithoutChangingState() {
        walletService.deposit(wallet.getId(), new BigDecimal("20.00"), key);
        Long originalTransactionId = history(wallet).get(0).getId();

        assertThrows(IdempotencyConflictException.class,
                () -> walletService.withdraw(wallet.getId(), new BigDecimal("20.00"), key));

        assertDepositStateUnchanged(originalTransactionId);
    }

    @Test
    void repeatedRequestShouldReturnOriginalResponseAfterAnotherOperation() throws Exception {
        IdempotencyResponse firstResponse = walletService.deposit(wallet.getId(), new BigDecimal("20.00"), key);
        walletService.deposit(wallet.getId(), new BigDecimal("50.00"), newRequestKey());
        IdempotencyResponse repeatedResponse = walletService.deposit(wallet.getId(), new BigDecimal("20.00"), key);

        assertThat(repeatedResponse).isEqualTo(firstResponse);
        assertThat(repeatedResponse.status()).isEqualTo(200);
        assertThat(objectMapper.readTree(repeatedResponse.body()).get("amount").decimalValue())
                .isEqualByComparingTo("20.00");
        assertBalance(wallet, "170.00");
        assertBalance(secondWallet, "100.00");
        assertThat(history(wallet)).hasSize(2)
                .allSatisfy(transaction -> assertThat(transaction.getType()).isEqualTo(TransactionType.DEPOSIT))
                .extracting(Transaction::getAmount)
                .containsExactlyInAnyOrder(new BigDecimal("20.00"), new BigDecimal("50.00"));
        assertThat(history(secondWallet)).isEmpty();
    }

    @Test
    void concurrentDepositsWithSameKeyShouldExecuteOnlyOnce() throws Exception {
        Long walletId = wallet.getId();
        UUID sameKey = key;
        CountDownLatch ready = new CountDownLatch(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<IdempotencyResponse> firstTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                return walletService.deposit(walletId, new BigDecimal("20.00"), sameKey);
            });
            Future<IdempotencyResponse> secondTask = executor.submit(() -> {
                ready.countDown();
                awaitBothTasksReady(ready);
                return walletService.deposit(walletId, new BigDecimal("20.00"), sameKey);
            });

            IdempotencyResponse firstResponse = firstTask.get(10, TimeUnit.SECONDS);
            IdempotencyResponse repeatedResponse = secondTask.get(10, TimeUnit.SECONDS);
            assertThat(repeatedResponse).isEqualTo(firstResponse);
            assertThat(firstResponse.status()).isEqualTo(200);
            assertThat(objectMapper.readTree(firstResponse.body()).get("amount").decimalValue())
                    .isEqualByComparingTo("20.00");
        }

        assertBalance(wallet, "120.00");
        assertBalance(secondWallet, "100.00");
        assertSingleOperation(wallet, TransactionType.DEPOSIT, "20.00");
        assertThat(history(secondWallet)).isEmpty();
    }

    @Test
    void failedWithdrawalShouldRollbackKeyAndAllowRetryAfterDeposit() {
        assertThrows(InsufficientBalanceException.class,
                () -> walletService.withdraw(wallet.getId(), new BigDecimal("150.00"), key));

        assertBalance(wallet, "100.00");
        assertThat(history(wallet)).isEmpty();
        assertThat(idempotencyRecordRepository.findById(key)).isEmpty();

        walletService.deposit(wallet.getId(), new BigDecimal("100.00"), newRequestKey());
        IdempotencyResponse successfulResponse = walletService.withdraw(
                wallet.getId(), new BigDecimal("150.00"), key);
        IdempotencyResponse repeatedResponse = walletService.withdraw(
                wallet.getId(), new BigDecimal("150.00"), key);

        assertThat(repeatedResponse).isEqualTo(successfulResponse);
        assertBalance(wallet, "50.00");
        assertBalance(secondWallet, "100.00");
        assertThat(history(secondWallet)).isEmpty();
        assertThat(history(wallet)).hasSize(2)
                .allSatisfy(transaction -> assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS));
        assertThat(history(wallet)).filteredOn(transaction -> transaction.getType() == TransactionType.WITHDRAWAL)
                .singleElement().satisfies(transaction ->
                        assertThat(transaction.getAmount()).isEqualByComparingTo("150.00"));
        assertCompletedRequest(key, successfulResponse);
    }

    @Test
    void failedTransferShouldRollbackKeyAndAllowRetryAfterRecipientWithdrawal() {
        // Same DECIMAL(19, 2) balance limit used by Wallet; fixture setup creates no financial operation.
        BigDecimal maxBalance = new BigDecimal("99999999999999999.99");
        transactionTemplate.executeWithoutResult(status -> {
            Wallet recipient = walletRepository.findByIdForUpdate(secondWallet.getId()).orElseThrow();
            recipient.deposit(maxBalance.subtract(recipient.getBalance()));
        });

        assertThrows(BalanceLimitExceededException.class,
                () -> walletService.transfer(wallet.getId(), secondWallet.getId(), new BigDecimal("20.00"), key));

        assertBalance(wallet, "100.00");
        assertBalance(secondWallet, maxBalance.toPlainString());
        assertThat(history(wallet)).isEmpty();
        assertThat(history(secondWallet)).isEmpty();
        assertThat(idempotencyRecordRepository.findById(key)).isEmpty();

        walletService.withdraw(secondWallet.getId(), new BigDecimal("20.00"), newRequestKey());
        IdempotencyResponse successfulResponse = walletService.transfer(
                wallet.getId(), secondWallet.getId(), new BigDecimal("20.00"), key);
        IdempotencyResponse repeatedResponse = walletService.transfer(
                wallet.getId(), secondWallet.getId(), new BigDecimal("20.00"), key);

        assertThat(repeatedResponse).isEqualTo(successfulResponse);
        assertBalance(wallet, "80.00");
        assertBalance(secondWallet, maxBalance.toPlainString());
        assertSingleOperation(wallet, TransactionType.TRANSFER, "20.00");
        assertThat(history(secondWallet)).hasSize(2)
                .allSatisfy(transaction -> assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS));
        assertThat(history(secondWallet)).filteredOn(transaction -> transaction.getType() == TransactionType.TRANSFER)
                .singleElement().satisfies(transaction -> {
                    assertThat(transaction.getId()).isEqualTo(history(wallet).get(0).getId());
                    assertThat(transaction.getAmount()).isEqualByComparingTo("20.00");
                });
        assertCompletedRequest(key, successfulResponse);
    }

    private List<Transaction> history(Wallet testWallet) {
        Long walletId = testWallet.getId();
        return transactionRepository.findHistory(walletId, null, null, Pageable.unpaged(), null, null).getContent();
    }

    private void assertBalance(Wallet testWallet, String expectedBalance) {
        assertThat(walletRepository.findById(testWallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(expectedBalance);
    }

    private void assertSingleOperation(Wallet testWallet, TransactionType type, String amount) {
        assertThat(history(testWallet)).singleElement().satisfies(transaction -> {
            assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(transaction.getType()).isEqualTo(type);
            assertThat(transaction.getAmount()).isEqualByComparingTo(amount);
        });
    }

    private void assertCompletedRequest(UUID requestKey, IdempotencyResponse response) {
        var record = idempotencyRecordRepository.findById(requestKey).orElseThrow();
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(response.status()).isEqualTo(200);
        assertThat(record.getResponseStatus()).isEqualTo(response.status());
        assertThat(record.getResponseBody()).isEqualTo(response.body());
    }

    private void assertDepositStateUnchanged(Long originalTransactionId) {
        assertBalance(wallet, "120.00");
        assertBalance(secondWallet, "100.00");
        assertSingleOperation(wallet, TransactionType.DEPOSIT, "20.00");
        assertThat(history(wallet).get(0).getId()).isEqualTo(originalTransactionId);
        assertThat(history(secondWallet)).isEmpty();
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
