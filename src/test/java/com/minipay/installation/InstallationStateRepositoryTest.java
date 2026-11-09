package com.minipay.installation;

import com.minipay.support.RepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InstallationStateRepositoryTest extends RepositoryTestSupport {
    @Autowired private InstallationStateRepository repository;

    @Test
    void migrationShouldLeaveInstallationStateEmpty() {
        assertThat(repository.count()).isZero();
    }

    @Test
    void initializationShouldReloadWithIdOneAndTimestamp() {
        repository.saveAndFlush(new InstallationState(CREATED));
        flushAndClear();
        InstallationState loaded = repository.findById(1L).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(1L);
        assertThat(loaded.getInitializedAt()).isEqualTo(CREATED);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 2, -1})
    void otherIdShouldBeRejected(long id) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO installation_state (id, initialized_at) VALUES (?, ?)",
                id, java.sql.Timestamp.from(CREATED)));
    }

    @Test
    void duplicateIdShouldBeRejectedByInsert() {
        repository.saveAndFlush(new InstallationState(CREATED));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO installation_state (id, initialized_at) VALUES (?, ?)",
                1L, java.sql.Timestamp.from(CREATED)));
    }

    @Test
    void missingInitializationTimeShouldBeRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO installation_state (id, initialized_at) VALUES (1, NULL)"));
    }
}
