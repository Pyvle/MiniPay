package com.minipay.session;

import com.minipay.support.RepositoryTestSupport;
import com.minipay.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionRepositoryTest extends RepositoryTestSupport {
    @Autowired private SessionRepository repository;

    @Test
    void newSessionShouldReloadWithUserAndDates() {
        User user = user("alice@example.com");
        Session saved = repository.saveAndFlush(new Session(user, CREATED, CREATED.plusSeconds(3600)));
        flushAndClear();
        Session loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getUser().getId()).isEqualTo(user.getId());
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED);
        assertThat(loaded.getExpiresAt()).isEqualTo(CREATED.plusSeconds(3600));
        assertThat(loaded.getRevokedAt()).isNull();
        assertThat(loaded.getRevokeReason()).isNull();
    }

    @Test
    void userMayHaveTwoSessions() {
        User user = user("alice@example.com");
        repository.saveAndFlush(new Session(user, CREATED, CREATED.plusSeconds(3600)));
        repository.saveAndFlush(new Session(user, CREATED, CREATED.plusSeconds(7200)));
        flushAndClear();
        assertThat(repository.findAll()).hasSize(2)
                .allSatisfy(s -> assertThat(s.getUser().getId()).isEqualTo(user.getId()));
    }

    @Test
    void revocationShouldSurviveReload() {
        Session saved = session();
        saved.setRevokedAt(CREATED.plusSeconds(60));
        saved.setRevokeReason("LOGOUT");
        repository.saveAndFlush(saved);
        flushAndClear();
        Session loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getRevokedAt()).isEqualTo(CREATED.plusSeconds(60));
        assertThat(loaded.getRevokeReason()).isEqualTo("LOGOUT");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void expirationMustBeAfterCreation(int seconds) {
        User user = user("alice@example.com");
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new Session(user, CREATED, CREATED.plusSeconds(seconds))));
    }

    @Test
    void revocationBeforeCreationShouldBeRejected() {
        Session saved = session();
        saved.setRevokedAt(CREATED.minusSeconds(1));
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(saved));
    }

    @Test
    void missingUserShouldBeRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO sessions (user_id, created_at, expires_at) VALUES (?, ?, ?)",
                -1L, java.sql.Timestamp.from(CREATED), java.sql.Timestamp.from(CREATED.plusSeconds(3600))));
    }
}
