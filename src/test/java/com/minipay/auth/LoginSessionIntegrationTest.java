package com.minipay.auth;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.session.Session;
import com.minipay.session.SessionRepository;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.user.UserState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.auth.session-ttl=7d")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class LoginSessionIntegrationTest {
    private static final String PASSWORD = "test-password-123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EntityManager entityManager;

    @Test
    void successfulLoginShouldPersistSessionForCorrectUserWithSevenDayExpiry() throws Exception {
        User user = account("alice@example.com", UserState.ACTIVE);
        account("other@example.com", UserState.ACTIVE);

        login("alice@example.com", PASSWORD).andExpect(status().isOk());
        flushAndClear();

        assertThat(sessionRepository.findAll()).singleElement().satisfies(session -> {
            assertThat(session.getUser().getId()).isEqualTo(user.getId());
            assertThat(session.getCreatedAt()).isNotNull();
            assertThat(session.getExpiresAt()).isEqualTo(session.getCreatedAt().plus(Duration.ofDays(7)));
            assertThat(session.getRevokedAt()).isNull();
            assertThat(session.getRevokeReason()).isNull();
        });
    }

    @Test
    void twoLoginsShouldPersistTwoDistinctSessionsForSameUser() throws Exception {
        User user = account("alice@example.com", UserState.ACTIVE);

        login("alice@example.com", PASSWORD).andExpect(status().isOk());
        login("alice@example.com", PASSWORD).andExpect(status().isOk());
        flushAndClear();

        var sessions = sessionRepository.findAll();
        assertThat(sessions).hasSize(2);
        assertThat(sessions).extracting(Session::getId).doesNotHaveDuplicates().doesNotContainNull();
        assertThat(sessions).allSatisfy(session ->
                assertThat(session.getUser().getId()).isEqualTo(user.getId()));
    }

    @ParameterizedTest
    @CsvSource({
            "alice@example.com, wrong-password, ACTIVE",
            "unknown@example.com, test-password-123, ACTIVE",
            "alice@example.com, test-password-123, BLOCKED"
    })
    void rejectedLoginShouldNotCreateSessions(String email, String password, UserState state)
            throws Exception {
        account("alice@example.com", state);

        login(email, password).andExpect(status().isUnauthorized());
        flushAndClear();

        assertThat(sessionRepository.count()).isZero();
    }

    private User account(String email, UserState state) {
        User user = new User("Alice", email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setState(state);
        return userRepository.saveAndFlush(user);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
