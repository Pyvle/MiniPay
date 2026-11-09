package com.minipay.installation;

import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.minipay.audit.AccessAudit;
import com.minipay.audit.AccessAuditRepository;
import com.minipay.audit.ActorSource;
import com.minipay.common.exception.EmailAlreadyExistsException;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.support.TestUsers;
import com.minipay.user.SystemRole;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.user.UserState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

// Без тестовой @Transactional: проверяем commit/rollback транзакции сервиса.
@SpringBootTest(properties = "app.command=none")
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class InstallationServiceIntegrationTest {
    @Autowired private InstallationService installationService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private InstallationStateRepository installationStateRepository;
    @MockitoSpyBean private AccessAuditRepository accessAuditRepository;

    @BeforeEach
    void prepareDatabase() {
        clearDatabase();
    }

    @AfterEach
    void cleanDatabase() {
        reset(installationStateRepository, accessAuditRepository);
        clearDatabase();
    }

    private void clearDatabase() {
        // Этот контекст использует отдельную PostgreSQL из Testcontainers.
        jdbc.execute("TRUNCATE users, wallets, transactions, idempotency_records, "
                + "roles, role_permissions, user_extra_permissions, role_assignable_roles, "
                + "role_grantable_permissions, sessions, refresh_tokens, access_audit, installation_state");
    }

    @Test
    void initializationShouldCommitAdminStateAndSafeAudit() {
        User created = initialize("Admin", "  Admin@Example.COM  ", "secret-password");
        User stored = userRepository.findById(created.getId()).orElseThrow();
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(stored.getEmail()).isEqualTo("admin@example.com");
        assertThat(stored.getSystemRole()).isEqualTo(SystemRole.SUPER_ADMIN);
        assertThat(stored.getState()).isEqualTo(UserState.ACTIVE);
        assertThat(stored.getMainRole()).isNull();
        assertThat(stored.getPasswordHash()).isNotEqualTo("secret-password");
        assertThat(passwordEncoder.matches("secret-password", stored.getPasswordHash())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_extra_permissions", Long.class)).isZero();
        assertThat(installationStateRepository.count()).isEqualTo(1);
        assertThat(installationStateRepository.findById(1L).orElseThrow().getInitializedAt()).isNotNull();
        assertThat(accessAuditRepository.count()).isEqualTo(1);
        AccessAudit event = accessAuditRepository.findAll().getFirst();
        assertThat(event.getAction()).isEqualTo("SUPER_ADMIN_INIT");
        assertThat(event.getActorSource()).isEqualTo(ActorSource.CLI);
        assertThat(event.getActorUserId()).isNull();
        assertThat(event.getTargetUserId()).isEqualTo(stored.getId());
        assertThat(event.getTargetRoleId()).isNull();
        assertThat(event.getBeforeData()).isNull();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getAfterData().size()).isEqualTo(2);
        assertThat(event.getAfterData().get("systemRole").asText()).isEqualTo("SUPER_ADMIN");
        assertThat(event.getAfterData().get("state").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void repeatedInitializationShouldLeaveExistingRecordsUnchanged() {
        User original = initialize("Admin", "admin@example.com", "first-password");
        String hash = original.getPasswordHash();
        var initializedAt = installationStateRepository.findById(1L).orElseThrow().getInitializedAt();
        assertThatThrownBy(() -> initialize("Another", "another@example.com", "second-password"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findById(original.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(installationStateRepository.count()).isEqualTo(1);
        assertThat(installationStateRepository.findById(1L).orElseThrow().getInitializedAt()).isEqualTo(initializedAt);
        assertThat(accessAuditRepository.count()).isEqualTo(1);
    }

    @Test
    void occupiedEmailShouldNotPromoteExistingUser() {
        User existing = userRepository.saveAndFlush(TestUsers.create("User", "taken@example.com"));
        String originalHash = existing.getPasswordHash();
        assertThatThrownBy(() -> initialize("Admin", " TAKEN@EXAMPLE.COM ", "secret-password"))
                .isInstanceOf(EmailAlreadyExistsException.class);
        User stored = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(stored.getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(stored.getPasswordHash()).isEqualTo(originalHash);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(installationStateRepository.count()).isZero();
        assertThat(accessAuditRepository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void invalidInputShouldLeaveNoRecords(String name, String email, String password) {
        assertThatThrownBy(() -> initialize(name, email, password))
                .isInstanceOf(IllegalArgumentException.class);
        assertEmpty();
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(
                Arguments.of(null, "admin@example.com", "12345678"),
                Arguments.of(" ", "admin@example.com", "12345678"),
                Arguments.of("a".repeat(101), "admin@example.com", "12345678"),
                Arguments.of("Admin", null, "12345678"),
                Arguments.of("Admin", " ", "12345678"),
                Arguments.of("Admin", "invalid-email", "12345678"),
                Arguments.of("Admin", "a".repeat(250) + "@example.com", "12345678"),
                Arguments.of("Admin", "admin@example.com", null),
                Arguments.of("Admin", "admin@example.com", "1234567"),
                Arguments.of("Admin", "admin@example.com", "a".repeat(129)),
                Arguments.of("Admin", "admin@example.com", "        "));
    }

    @Test
    void auditFailureShouldRollBackUserAndInstallationState() {
        doThrow(new IllegalStateException("Audit storage unavailable"))
                .when(accessAuditRepository).saveAndFlush(any(AccessAudit.class));
        assertThatThrownBy(() -> initialize("Admin", "admin@example.com", "secret-password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Audit storage unavailable");
        assertEmpty();
    }

    @Test
    void concurrentInitializationShouldCommitExactlyOneCompleteInstallation() throws Exception {
        CyclicBarrier bothCheckedState = new CyclicBarrier(2);
        doAnswer(invocation -> {
            boolean result = jdbc.queryForObject(
                    "SELECT count(*) FROM installation_state WHERE id = 1", Long.class) > 0;
            bothCheckedState.await(10, TimeUnit.SECONDS);
            return result;
        }).when(installationStateRepository).existsById(1L);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> initialize("First", "first@example.com", "first-password"));
            var second = executor.submit(() -> initialize("Second", "second@example.com", "second-password"));
            int successes = 0;
            int failures = 0;
            for (var future : java.util.List.of(first, second)) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (ExecutionException failure) {
                    // Реальный проигравший INSERT должен нарушить PK installation_state.
                    Throwable cause = failure.getCause();
                    boolean stateConflict = false;
                    while (cause != null) {
                        if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                            stateConflict = "23505".equals(violation.getSQLState())
                                    && "installation_state_pkey".equals(violation.getConstraintName());
                            break;
                        }
                        cause = cause.getCause();
                    }
                    assertThat(stateConflict)
                            .withFailMessage("Expected installation_state PK conflict, got: %s", failure.getCause())
                            .isTrue();
                    failures++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(failures).isEqualTo(1);
        }
        assertThat(userRepository.count()).isEqualTo(1);
        User winner = userRepository.findAll().getFirst();
        assertThat(winner.getSystemRole()).isEqualTo(SystemRole.SUPER_ADMIN);
        assertThat(winner.getState()).isEqualTo(UserState.ACTIVE);
        assertThat(installationStateRepository.count()).isEqualTo(1);
        assertThat(accessAuditRepository.count()).isEqualTo(1);
        assertThat(accessAuditRepository.findAll().getFirst().getTargetUserId()).isEqualTo(winner.getId());
    }

    private User initialize(String name, String email, String password) {
        char[] characters = password == null ? null : password.toCharArray();
        try {
            return installationService.initialize(name, email, characters);
        } finally {
            if (characters != null) {
                Arrays.fill(characters, '\0');
            }
        }
    }

    private void assertEmpty() {
        assertThat(userRepository.count()).isZero();
        assertThat(installationStateRepository.count()).isZero();
        assertThat(accessAuditRepository.count()).isZero();
    }
}
