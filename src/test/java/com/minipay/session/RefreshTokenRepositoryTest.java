package com.minipay.session;

import com.minipay.support.RepositoryTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenRepositoryTest extends RepositoryTestSupport {
    @Autowired private RefreshTokenRepository repository;

    @Test
    void newTokenShouldReloadWithSessionAndDates() {
        Session session = session();
        RefreshToken saved = repository.saveAndFlush(
                new RefreshToken(session, "hash-one", CREATED, CREATED.plusSeconds(3600)));
        flushAndClear();
        RefreshToken loaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getSession().getId()).isEqualTo(session.getId());
        assertThat(loaded.getTokenHash()).isEqualTo("hash-one");
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED);
        assertThat(loaded.getExpiresAt()).isEqualTo(CREATED.plusSeconds(3600));
        assertThat(loaded.getUsedAt()).isNull();
    }

    @Test
    void sessionMayHaveSeveralTokens() {
        Session session = session();
        repository.saveAndFlush(new RefreshToken(session, "hash-one", CREATED, CREATED.plusSeconds(3600)));
        repository.saveAndFlush(new RefreshToken(session, "hash-two", CREATED, CREATED.plusSeconds(3600)));
        flushAndClear();
        assertThat(repository.findAll()).hasSize(2)
                .allSatisfy(t -> assertThat(t.getSession().getId()).isEqualTo(session.getId()));
    }

    @Test
    void usageTimeShouldSurviveReload() {
        RefreshToken token = new RefreshToken(session(), "hash-one", CREATED, CREATED.plusSeconds(3600));
        token.setUsedAt(CREATED.plusSeconds(30));
        repository.saveAndFlush(token);
        flushAndClear();
        assertThat(repository.findById(token.getId()).orElseThrow().getUsedAt())
                .isEqualTo(CREATED.plusSeconds(30));
    }

    @Test
    void duplicateHashShouldBeRejected() {
        Session session = session();
        repository.saveAndFlush(new RefreshToken(session, "hash-one", CREATED, CREATED.plusSeconds(3600)));
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new RefreshToken(session, "hash-one", CREATED, CREATED.plusSeconds(3600))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankHashShouldBeRejected(String hash) {
        Session session = session();
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new RefreshToken(session, hash, CREATED, CREATED.plusSeconds(3600))));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void expirationMustBeAfterCreation(int seconds) {
        Session session = session();
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(
                new RefreshToken(session, "hash-one", CREATED, CREATED.plusSeconds(seconds))));
    }

    @Test
    void usageBeforeCreationShouldBeRejected() {
        RefreshToken token = new RefreshToken(session(), "hash-one", CREATED, CREATED.plusSeconds(3600));
        token.setUsedAt(CREATED.minusSeconds(1));
        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(token));
    }

    @Test
    void missingSessionShouldBeRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO refresh_tokens (session_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?)",
                -1L, "hash-one", java.sql.Timestamp.from(CREATED), java.sql.Timestamp.from(CREATED.plusSeconds(3600))));
    }
}
