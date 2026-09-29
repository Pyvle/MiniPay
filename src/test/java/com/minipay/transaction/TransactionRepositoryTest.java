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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    @Test
    void historyShouldIncludeFromExcludeToAndCombineAllFilters() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 12, 0);
        LocalDateTime to = from.plusHours(2);
        Transaction before = Transaction.deposit(savedWallet1, new BigDecimal("1.00"));
        Transaction atFrom = Transaction.transfer(savedWallet1, savedWallet2, new BigDecimal("2.00"));
        Transaction inside = Transaction.transfer(savedWallet3, savedWallet1, new BigDecimal("3.00"));
        Transaction atTo = Transaction.withdraw(savedWallet1, new BigDecimal("4.00"));
        Transaction after = Transaction.deposit(savedWallet1, new BigDecimal("5.00"));
        Transaction otherType = Transaction.deposit(savedWallet1, new BigDecimal("6.00"));
        Transaction otherStatus = Transaction.transfer(savedWallet1, savedWallet2, new BigDecimal("7.00"));
        Transaction unrelated = Transaction.transfer(savedWallet2, savedWallet3, new BigDecimal("8.00"));
        ReflectionTestUtils.setField(before, "createdAt", from.minusSeconds(1));
        ReflectionTestUtils.setField(atFrom, "createdAt", from);
        ReflectionTestUtils.setField(inside, "createdAt", from.plusHours(1));
        ReflectionTestUtils.setField(atTo, "createdAt", to);
        ReflectionTestUtils.setField(after, "createdAt", to.plusSeconds(1));
        for (Transaction transaction : List.of(otherType, otherStatus, unrelated)) {
            ReflectionTestUtils.setField(transaction, "createdAt", from.plusMinutes(30));
        }
        ReflectionTestUtils.setField(otherStatus, "status", TransactionStatus.FAILED);
        transactionRepository.saveAllAndFlush(List.of(before, atFrom, inside, atTo, after,
                otherType, otherStatus, unrelated));
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        assertThat(transactionRepository.findHistory(savedWallet1.getId(), null, null, pageable, from, null)
                .getContent()).extracting(Transaction::getId).containsExactlyInAnyOrder(
                        atFrom.getId(), inside.getId(), atTo.getId(), after.getId(), otherType.getId(), otherStatus.getId());
        assertThat(transactionRepository.findHistory(savedWallet1.getId(), null, null, pageable, null, to)
                .getContent()).extracting(Transaction::getId).containsExactlyInAnyOrder(
                        before.getId(), atFrom.getId(), inside.getId(), otherType.getId(), otherStatus.getId());

        Page<Transaction> first = transactionRepository.findHistory(savedWallet1.getId(), TransactionType.TRANSFER,
                TransactionStatus.SUCCESS, PageRequest.of(0, 1, pageable.getSort()), from, to);
        Page<Transaction> last = transactionRepository.findHistory(savedWallet1.getId(), TransactionType.TRANSFER,
                TransactionStatus.SUCCESS, PageRequest.of(1, 1, pageable.getSort()), from, to);
        assertThat(first.getContent()).extracting(Transaction::getId).containsExactly(inside.getId());
        assertThat(last.getContent()).extracting(Transaction::getId).containsExactly(atFrom.getId());
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(last.getTotalElements()).isEqualTo(2);
        Page<Transaction> equalBounds = transactionRepository.findHistory(
                savedWallet1.getId(), null, null, pageable, from, from);
        assertThat(equalBounds.getContent()).isEmpty();
        assertThat(equalBounds.getTotalElements()).isZero();
        assertThat(transactionRepository.findHistory(savedWallet1.getId(), null, null, pageable,
                to.plusDays(1), to.plusDays(2)).getContent()).isEmpty();
    }

    @Test
    void historyShouldApplyOptionalFiltersToBothSidesAndCountMatchingRows() {
        Transaction deposit = Transaction.deposit(savedWallet1, new BigDecimal("100.00"));
        Transaction withdrawal = Transaction.withdraw(savedWallet1, new BigDecimal("10.00"));
        Transaction outgoing = Transaction.transfer(savedWallet1, savedWallet2, new BigDecimal("20.00"));
        Transaction incoming = Transaction.transfer(savedWallet3, savedWallet1, new BigDecimal("30.00"));
        Transaction failed = Transaction.transfer(savedWallet1, savedWallet2, new BigDecimal("5.00"));
        // Persisted fixture isolates status filtering; production operations still create SUCCESS only.
        ReflectionTestUtils.setField(failed, "status", TransactionStatus.FAILED);
        Transaction unrelated = Transaction.transfer(savedWallet2, savedWallet3, new BigDecimal("40.00"));
        transactionRepository.saveAllAndFlush(List.of(deposit, withdrawal, outgoing, incoming, failed, unrelated));
        PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        assertThat(transactionRepository.findHistory(savedWallet1.getId(), TransactionType.DEPOSIT, null, pageable, null, null)
                .getContent()).extracting(Transaction::getId).containsExactly(deposit.getId());
        assertThat(transactionRepository.findHistory(savedWallet1.getId(), TransactionType.WITHDRAWAL, null, pageable, null, null)
                .getContent()).extracting(Transaction::getId).containsExactly(withdrawal.getId());
        assertThat(transactionRepository.findHistory(savedWallet1.getId(), null, TransactionStatus.SUCCESS, pageable, null, null)
                .getContent()).extracting(Transaction::getId)
                .containsExactlyInAnyOrder(deposit.getId(), withdrawal.getId(), outgoing.getId(), incoming.getId());
        assertThat(transactionRepository.findHistory(savedWallet1.getId(), TransactionType.TRANSFER, null, pageable, null, null)
                .getContent()).extracting(Transaction::getId)
                .containsExactlyInAnyOrder(outgoing.getId(), incoming.getId(), failed.getId());
        assertThat(transactionRepository.findHistory(savedWallet1.getId(), null, TransactionStatus.FAILED, pageable, null, null)
                .getContent()).extracting(Transaction::getId).containsExactly(failed.getId());

        Page<Transaction> first = transactionRepository.findHistory(savedWallet1.getId(),
                TransactionType.TRANSFER, TransactionStatus.SUCCESS, PageRequest.of(0, 1, pageable.getSort()), null, null);
        Page<Transaction> second = transactionRepository.findHistory(savedWallet1.getId(),
                TransactionType.TRANSFER, TransactionStatus.SUCCESS, PageRequest.of(1, 1, pageable.getSort()), null, null);
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getTotalElements()).isEqualTo(2);
        assertThat(java.util.stream.Stream.concat(first.stream(), second.stream()).map(Transaction::getId).toList())
                .containsExactlyInAnyOrder(outgoing.getId(), incoming.getId());
        Page<Transaction> empty = transactionRepository.findHistory(savedWallet1.getId(),
                TransactionType.DEPOSIT, TransactionStatus.FAILED, pageable, null, null);
        assertThat(empty.getContent()).isEmpty();
        assertThat(empty.getTotalElements()).isZero();
    }

    @Test
    void historyShouldUseDescendingIdWhenTimestampsAreEqualAcrossPages() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 9, 1, 12, 0);
        Transaction first = Transaction.deposit(savedWallet1, new BigDecimal("10.00"));
        Transaction second = Transaction.withdraw(savedWallet1, new BigDecimal("5.00"));
        ReflectionTestUtils.setField(first, "createdAt", timestamp);
        ReflectionTestUtils.setField(second, "createdAt", timestamp);
        transactionRepository.saveAndFlush(first);
        transactionRepository.saveAndFlush(second);
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        assertThat(transactionRepository.findHistory(savedWallet1.getId(), null, null, PageRequest.of(0, 1, sort), null, null)
                .getContent()).extracting(Transaction::getId).containsExactly(second.getId());
        assertThat(transactionRepository.findHistory(savedWallet1.getId(), null, null, PageRequest.of(1, 1, sort), null, null)
                .getContent()).extracting(Transaction::getId).containsExactly(first.getId());
    }

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

        Page<Transaction> result = transactionRepository
                .findHistory(savedWallet1.getId(), null, null, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);

        assertThat(result).hasSize(4);
        assertThat(result.getTotalElements()).isEqualTo(4);

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

        Page<Transaction> result = transactionRepository
                .findHistory(savedWallet1.getId(), null, null, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
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

        Page<Transaction> result = transactionRepository
                .findHistory(savedWallet1.getId(), null, null, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);

        assertThat(result).hasSize(3);

        assertThat(result)
                .extracting(Transaction::getId)
                .containsExactly(
                        newestTransaction.getId(),
                        middleTransaction.getId(),
                        oldestTransaction.getId());

        assertThat(result.getContent().get(0).getCreatedAt())
                .isAfter(result.getContent().get(1).getCreatedAt());

        assertThat(result.getContent().get(1).getCreatedAt())
                .isAfter(result.getContent().get(2).getCreatedAt());
    }

    @Test
    void findAllShouldReturnEmptyListWhenTransactionsDoNotExist() {
        Page<Transaction> result = transactionRepository
                .findHistory(savedWallet1.getId(), null, null, PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);

        assertThat(result).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void findAllShouldReturnRequestedPagesWithFilteredTotals() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 12, 0);
        Transaction oldest = Transaction.deposit(savedWallet1, new BigDecimal("10.00"));
        Transaction middle = Transaction.transfer(savedWallet1, savedWallet2, new BigDecimal("20.00"));
        Transaction newest = Transaction.transfer(savedWallet3, savedWallet1, new BigDecimal("30.00"));
        ReflectionTestUtils.setField(oldest, "createdAt", start);
        ReflectionTestUtils.setField(middle, "createdAt", start.plusHours(1));
        ReflectionTestUtils.setField(newest, "createdAt", start.plusHours(2));
        transactionRepository.saveAllAndFlush(List.of(middle, oldest, newest,
                Transaction.deposit(savedWallet2, new BigDecimal("40.00"))));

        Page<Transaction> first = transactionRepository
                .findHistory(savedWallet1.getId(), null, null, PageRequest.of(0, 2, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);
        Page<Transaction> last = transactionRepository
                .findHistory(savedWallet1.getId(), null, null, PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);
        Page<Transaction> beyond = transactionRepository
                .findHistory(savedWallet1.getId(), null, null, PageRequest.of(2, 2, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))), null, null);

        assertThat(first.getContent()).extracting(Transaction::getId)
                .containsExactly(newest.getId(), middle.getId());
        assertThat(first.getSize()).isEqualTo(2);
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(last.getContent()).extracting(Transaction::getId).containsExactly(oldest.getId());
        assertThat(last.getNumber()).isEqualTo(1);
        assertThat(last.getTotalElements()).isEqualTo(3);
        assertThat(beyond.getContent()).isEmpty();
        assertThat(beyond.getTotalElements()).isEqualTo(3);
    }
}
