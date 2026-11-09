package com.minipay.session;

import java.time.Instant;
import java.util.Optional;

import com.minipay.user.User;
import com.minipay.user.UserState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAuthenticationServiceTest {
    private Instant now;
    private static final Long USER_ID = 12L;
    private static final Long SESSION_ID = 34L;

    @Mock private SessionRepository sessionRepository;
    private SessionAuthenticationService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SessionAuthenticationService(sessionRepository);
        now = Instant.now();
        user = new User("Alice", "alice@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setState(UserState.ACTIVE);
    }

    @Test
    void activeSessionShouldReturnItsUser() {
        sessionEndingAt(now.plusSeconds(3600));
        assertThat(service.authenticate(USER_ID, SESSION_ID)).isSameAs(user);
    }

    @Test
    void missingSessionShouldBeRejected() {
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());
        rejected(USER_ID, SESSION_ID);
    }

    @Test
    void anotherUsersSessionShouldBeRejected() {
        sessionEndingAt(now.plusSeconds(3600));
        rejected(99L, SESSION_ID);
    }

    @Test
    void revokedSessionShouldBeRejected() {
        Session session = sessionEndingAt(now.plusSeconds(3600));
        session.setRevokedAt(now.minusSeconds(1));
        session.setRevokeReason("LOGOUT");
        rejected(USER_ID, SESSION_ID);
    }

    @Test
    void expiredSessionShouldBeRejected() {
        sessionEndingAt(now.minusSeconds(1));
        rejected(USER_ID, SESSION_ID);
    }

    @Test
    void expirationExactlyNowShouldBeRejected() {
        sessionEndingAt(now);
        try (var instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(now);
            rejected(USER_ID, SESSION_ID);
        }
    }

    @Test
    void expirationOneNanosecondInFutureShouldBeAccepted() {
        sessionEndingAt(now.plusNanos(1));
        try (var instant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instant.when(Instant::now).thenReturn(now);
            assertThat(service.authenticate(USER_ID, SESSION_ID)).isSameAs(user);
        }
    }

    @Test
    void blockedUserShouldBeRejectedDespiteValidSession() {
        sessionEndingAt(now.plusSeconds(3600));
        user.setState(UserState.BLOCKED);
        rejected(USER_ID, SESSION_ID);
    }

    @ParameterizedTest
    @CsvSource(value = {"null,34", "12,null", "null,null"}, nullValues = "null")
    void nullIdentifiersShouldBeRejectedWithoutDatabaseLookup(Long userId, Long sessionId) {
        rejected(userId, sessionId);
        verifyNoInteractions(sessionRepository);
    }

    private Session sessionEndingAt(Instant expiresAt) {
        Session session = new Session(user, now.minusSeconds(3600), expiresAt);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        return session;
    }

    private void rejected(Long userId, Long sessionId) {
        assertThatThrownBy(() -> service.authenticate(userId, sessionId))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid authentication");
    }
}
