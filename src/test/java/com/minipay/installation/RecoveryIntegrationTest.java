package com.minipay.installation;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.audit.AccessAudit;
import com.minipay.audit.AccessAuditRepository;
import com.minipay.audit.ActorSource;
import com.minipay.auth.AuthService;
import com.minipay.auth.dto.LoginResponse;
import com.minipay.common.exception.InvalidCredentialsException;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.user.SystemRole;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.user.UserState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.command=none",
        "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.datasource.hikari.connection-init-sql=SET lock_timeout = '10s'"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class RecoveryIntegrationTest {
    private static final String OLD_PASSWORD = "old-test-password-123";
    private static final String NEW_PASSWORD = "new-test-password-456";
    private final String adminEmail = "recover-admin-" + UUID.randomUUID() + "@example.com";
    private final String otherEmail = "recover-user-" + UUID.randomUUID() + "@example.com";

    @Autowired private InstallationService installation;
    @Autowired private InstallationStateRepository installationState;
    @Autowired private AuthService auth;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockitoSpyBean private AccessAuditRepository audit;

    @Test
    void recoveryShouldUnblockAdminAndRevokeOnlyTheirPreviousSessions() throws Exception {
        User admin = createUser(adminEmail, SystemRole.SUPER_ADMIN);
        User other = createUser(otherEmail, SystemRole.USER);
        LoginResponse first = auth.login(adminEmail, OLD_PASSWORD);
        LoginResponse second = auth.login(adminEmail, OLD_PASSWORD);
        LoginResponse unrelated = auth.login(otherEmail, OLD_PASSWORD);
        block(admin);
        long installationCount = installationState.count();

        recover(" " + adminEmail.toUpperCase(Locale.ROOT) + " ");

        User stored = reload(admin);
        assertThat(stored.getState()).isEqualTo(UserState.ACTIVE);
        assertThat(stored.getSystemRole()).isEqualTo(SystemRole.SUPER_ADMIN);
        assertThat(stored.getEmail()).isEqualTo(adminEmail);
        assertThat(stored.getPasswordHash()).isNotEqualTo(admin.getPasswordHash());
        assertThat(passwords.matches(NEW_PASSWORD, stored.getPasswordHash())).isTrue();
        assertThatThrownBy(() -> auth.login(adminEmail, OLD_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
        assertRejected(first);
        assertRejected(second);
        me(unrelated).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(other.getId()));
        refresh(unrelated).andExpect(status().isOk());
        me(auth.login(adminEmail, NEW_PASSWORD)).andExpect(status().isOk());
        assertThat(installationState.count()).isEqualTo(installationCount);

        assertThat(events(admin)).singleElement().satisfies(event -> {
            assertThat(event.getAction()).isEqualTo("SUPER_ADMIN_RECOVER");
            assertThat(event.getActorSource()).isEqualTo(ActorSource.CLI);
            assertThat(event.getBeforeData().toString()).isEqualTo("{\"state\":\"BLOCKED\"}");
            assertThat(event.getAfterData().toString()).isEqualTo("{\"state\":\"ACTIVE\"}");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryUserOrUnknownEmailShouldBeRejectedWithoutChanges(boolean unknownEmail) throws Exception {
        User other = createUser(otherEmail, SystemRole.USER);
        LoginResponse pair = auth.login(otherEmail, OLD_PASSWORD);
        long userCount = users.count();
        long auditCount = audit.count();
        long installationCount = installationState.count();

        assertThatThrownBy(() -> recover(unknownEmail ? adminEmail : otherEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SUPER_ADMIN account not found");

        User stored = reload(other);
        assertThat(stored.getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(stored.getState()).isEqualTo(UserState.ACTIVE);
        assertThat(stored.getPasswordHash()).isEqualTo(other.getPasswordHash());
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(audit.count()).isEqualTo(auditCount);
        assertThat(installationState.count()).isEqualTo(installationCount);
        me(pair).andExpect(status().isOk());
        refresh(pair).andExpect(status().isOk());
    }

    @Test
    void auditFailureShouldRollBackPasswordStateAndSessionRevocation() {
        User admin = createUser(adminEmail, SystemRole.SUPER_ADMIN);
        auth.login(adminEmail, OLD_PASSWORD);
        auth.login(adminEmail, OLD_PASSWORD);
        block(admin);
        doThrow(new IllegalStateException("Audit storage unavailable"))
                .when(audit).saveAndFlush(any(AccessAudit.class));

        assertThatThrownBy(() -> recover(adminEmail))
                .isInstanceOf(IllegalStateException.class).hasMessage("Audit storage unavailable");

        User stored = reload(admin);
        assertThat(stored.getState()).isEqualTo(UserState.BLOCKED);
        assertThat(stored.getPasswordHash()).isEqualTo(admin.getPasswordHash());
        assertThat(jdbc.queryForList("SELECT revoked_at IS NULL AND revoke_reason IS NULL "
                + "FROM sessions WHERE user_id = ?", Boolean.class, admin.getId()))
                .containsExactly(true, true);
        assertThat(events(admin)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void loginAndRecoveryShouldSerializeOnTheUserRow(boolean loginFirst) throws Exception {
        User admin = createUser(adminEmail, SystemRole.SUPER_ADMIN);
        CountDownLatch firstOperationFinished = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        var firstBackendPid = new java.util.concurrent.atomic.AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.execute(tx -> {
                firstBackendPid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                LoginResponse pair = null;
                if (loginFirst) {
                    pair = auth.login(adminEmail, OLD_PASSWORD);
                } else {
                    recover(adminEmail);
                }
                firstOperationFinished.countDown();
                await(allowCommit);
                return pair;
            }));

            try {
                await(firstOperationFinished);
                var second = executor.submit(() -> {
                    if (loginFirst) {
                        recover(adminEmail);
                    } else {
                        assertThatThrownBy(() -> auth.login(adminEmail, OLD_PASSWORD))
                                .isInstanceOf(InvalidCredentialsException.class);
                    }
                });
                waitForBlockedBackend(firstBackendPid.get());
                allowCommit.countDown();

                LoginResponse pair = first.get(15, TimeUnit.SECONDS);
                second.get(15, TimeUnit.SECONDS);
                if (loginFirst) {
                    assertRejected(pair);
                    assertThat(jdbc.queryForList("SELECT revoke_reason FROM sessions WHERE user_id = ?",
                            String.class, admin.getId())).containsExactly("SUPER_ADMIN_RECOVERY");
                } else {
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions WHERE user_id = ?",
                            Long.class, admin.getId())).isZero();
                }
            } finally {
                allowCommit.countDown();
            }
        }
    }

    private void waitForBlockedBackend(int blockerPid) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        do {
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                        WHERE ? = ANY(pg_blocking_pids(pid)))
                    """, Boolean.class, blockerPid))) {
                return;
            }
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("The second operation did not wait for the first transaction");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the concurrent operation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent operation interrupted", exception);
        }
    }

    private User createUser(String email, SystemRole role) {
        User user = new User("Recovery Test", email);
        user.setPasswordHash(passwords.encode(OLD_PASSWORD));
        user.setSystemRole(role);
        user.setState(UserState.ACTIVE);
        return users.saveAndFlush(user);
    }

    private void recover(String email) {
        char[] password = NEW_PASSWORD.toCharArray();
        try {
            installation.recover(email, password);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void block(User user) {
        jdbc.update("UPDATE users SET state = 'BLOCKED' WHERE id = ?", user.getId());
    }

    private User reload(User user) {
        return users.findById(user.getId()).orElseThrow();
    }

    private java.util.List<AccessAudit> events(User user) {
        return audit.findAll().stream().filter(event -> user.getId().equals(event.getTargetUserId())).toList();
    }

    private void assertRejected(LoginResponse pair) throws Exception {
        me(pair).andExpect(status().isUnauthorized());
        refresh(pair).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions me(LoginResponse pair) throws Exception {
        return mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + pair.accessToken()));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(LoginResponse pair) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", pair.refreshToken()))));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM access_audit WHERE target_user_id IN (SELECT id FROM users WHERE email IN (?, ?))",
                adminEmail, otherEmail);
        jdbc.update("DELETE FROM refresh_tokens WHERE session_id IN (SELECT s.id FROM sessions s "
                + "JOIN users u ON u.id = s.user_id WHERE u.email IN (?, ?))", adminEmail, otherEmail);
        jdbc.update("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE email IN (?, ?))",
                adminEmail, otherEmail);
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", adminEmail, otherEmail);
    }
}
