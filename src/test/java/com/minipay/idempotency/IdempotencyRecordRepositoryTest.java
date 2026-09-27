package com.minipay.idempotency;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minipay.support.PostgresTestConfiguration;
import com.minipay.transaction.TransactionType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class IdempotencyRecordRepositoryTest {

    @Autowired
    private IdempotencyRecordRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void tryReserveShouldCreateProcessingRecord() {
        UUID key = UUID.randomUUID();

        int inserted = repository.tryReserve(
                key, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("20.00"));

        assertThat(inserted).isEqualTo(1);
        IdempotencyRecord record = repository.findById(key).orElseThrow();
        assertThat(record.getIdempotencyKey()).isEqualTo(key);
        assertThat(record.getOperationType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(record.getWalletId()).isEqualTo(10L);
        assertThat(record.getToWalletId()).isNull();
        assertThat(record.getAmount()).isEqualByComparingTo("20.00");
        assertThat(record.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(record.getResponseStatus()).isNull();
        assertThat(record.getResponseBody()).isNull();
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void tryReserveShouldReturnZeroForRepeatedKey() {
        UUID key = UUID.randomUUID();

        int first = repository.tryReserve(
                key, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("20.00"));
        int second = repository.tryReserve(
                key, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("20.00"));

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void tryReserveShouldPreserveOriginalAmountForRepeatedKey() {
        UUID key = UUID.randomUUID();
        assertThat(repository.tryReserve(
                key, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("20.00")))
                .isEqualTo(1);

        int inserted = repository.tryReserve(
                key, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("50.00"));

        assertThat(inserted).isZero();
        entityManager.clear();
        IdempotencyRecord record = repository.findById(key).orElseThrow();
        assertThat(record.getAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void tryReserveShouldCreateIndependentRecordForDifferentKey() {
        UUID firstKey = UUID.randomUUID();
        UUID secondKey = UUID.randomUUID();

        assertThat(repository.tryReserve(
                firstKey, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("20.00")))
                .isEqualTo(1);
        assertThat(repository.tryReserve(
                secondKey, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("20.00")))
                .isEqualTo(1);

        IdempotencyRecord first = repository.findById(firstKey).orElseThrow();
        IdempotencyRecord second = repository.findById(secondKey).orElseThrow();
        first.complete(200, "{\"balance\":120.00}");
        entityManager.flush();
        entityManager.clear();

        assertThat(repository.findById(firstKey).orElseThrow().getStatus())
                .isEqualTo(IdempotencyStatus.COMPLETED);
        second = repository.findById(secondKey).orElseThrow();
        assertThat(second.getIdempotencyKey()).isEqualTo(secondKey);
        assertThat(second.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(second.getResponseStatus()).isNull();
        assertThat(second.getResponseBody()).isNull();
    }

    @Test
    void completeShouldPersistResponseAndPreserveRequestParameters() {
        UUID key = UUID.randomUUID();
        repository.tryReserve(
                key, TransactionType.TRANSFER.name(), 10L, 20L, new BigDecimal("20.00"));
        IdempotencyRecord record = repository.findById(key).orElseThrow();
        var createdAt = record.getCreatedAt();

        record.complete(200, "{\"balance\":120.00}");
        entityManager.flush();
        entityManager.clear();

        IdempotencyRecord persisted = repository.findById(key).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(persisted.getResponseStatus()).isEqualTo(200);
        assertThat(persisted.getResponseBody()).isEqualTo("{\"balance\":120.00}");
        assertThat(persisted.getIdempotencyKey()).isEqualTo(key);
        assertThat(persisted.getOperationType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(persisted.getWalletId()).isEqualTo(10L);
        assertThat(persisted.getToWalletId()).isEqualTo(20L);
        assertThat(persisted.getAmount()).isEqualByComparingTo("20.00");
        assertThat(persisted.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void completeShouldRejectRepeatedCompletionAndPreserveOriginalResponse() {
        UUID key = UUID.randomUUID();
        repository.tryReserve(
                key, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("20.00"));
        IdempotencyRecord record = repository.findById(key).orElseThrow();
        record.complete(200, "{\"balance\":120.00}");

        assertThatThrownBy(() -> record.complete(201, "{\"balance\":140.00}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Only a processing record can be completed");
        entityManager.flush();
        entityManager.clear();

        IdempotencyRecord persisted = repository.findById(key).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(persisted.getResponseStatus()).isEqualTo(200);
        assertThat(persisted.getResponseBody()).isEqualTo("{\"balance\":120.00}");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    void completeShouldRejectMissingOrBlankResponseBody(String responseBody) {
        UUID key = UUID.randomUUID();
        repository.tryReserve(
                key, TransactionType.DEPOSIT.name(), 10L, null, new BigDecimal("20.00"));
        IdempotencyRecord record = repository.findById(key).orElseThrow();

        assertThatThrownBy(() -> record.complete(200, responseBody))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Response body must not be blank");
        entityManager.flush();
        entityManager.clear();

        IdempotencyRecord persisted = repository.findById(key).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(IdempotencyStatus.PROCESSING);
        assertThat(persisted.getResponseStatus()).isNull();
        assertThat(persisted.getResponseBody()).isNull();
    }
}
