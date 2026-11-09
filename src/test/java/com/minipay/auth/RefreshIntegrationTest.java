package com.minipay.auth;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.auth.token.AccessTokenService;
import com.minipay.session.RefreshTokenService;
import com.minipay.support.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// No enclosing test transaction: requests must commit rotation and revocation themselves.
@SpringBootTest(properties = {
        "app.auth.session-ttl=7d",
        "app.auth.access-token-ttl=15m",
        "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class RefreshIntegrationTest {
    private static final String PASSWORD = "refresh-test-password-123";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RefreshTokenService tokens;
    @Autowired private JwtDecoder decoder;
    @MockitoSpyBean private AccessTokenService accessTokens;

    private String email;
    private long userId;
    private long sessionId;
    private JsonNode original;

    @BeforeEach
    void registerAndLogin() throws Exception {
        email = "refresh-" + UUID.randomUUID() + "@example.com";
        MvcResult registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "name", "Refresh Test", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn();
        userId = body(registration).path("id").longValue();
        original = login();
        sessionId = ((Number) decoder.decode(original.path("accessToken").asText())
                .getClaims().get("sid")).longValue();
    }

    @AfterEach
    void cleanupOnlyThisAccount() {
        if (email == null) return;
        jdbc.update("DELETE FROM refresh_tokens WHERE session_id IN "
                + "(SELECT id FROM sessions WHERE user_id IN (SELECT id FROM users WHERE email = ?))", email);
        jdbc.update("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
        jdbc.update("DELETE FROM users WHERE email = ?", email);
    }

    @Test
    void rotationShouldPreserveSessionAndExpiryAndStoreOnlyHash() throws Exception {
        Instant sessionExpiry = sessionExpiry();
        JsonNode rotated = body(refresh(raw(original), 200));
        String newRaw = raw(rotated);
        assertThat(newRaw).isNotBlank().isNotEqualTo(raw(original));
        assertThat(rotated.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(Instant.parse(rotated.path("refreshExpiresAt").asText())).isEqualTo(sessionExpiry);
        assertThat(sessionExpiry()).isEqualTo(sessionExpiry);
        assertThat(Instant.parse(rotated.path("accessExpiresAt").asText())).isBeforeOrEqualTo(sessionExpiry);
        assertThat(tokenCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT used_at IS NOT NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class, tokens.hashToken(raw(original)))).isTrue();
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM refresh_tokens WHERE token_hash = ?",
                Boolean.class, tokens.hashToken(newRaw))).isTrue();
        assertThat(jdbc.queryForObject("SELECT session_id FROM refresh_tokens WHERE token_hash = ?",
                Long.class, tokens.hashToken(newRaw))).isEqualTo(sessionId);
        assertThat(jdbc.queryForObject("SELECT expires_at FROM refresh_tokens WHERE token_hash = ?",
                OffsetDateTime.class, tokens.hashToken(newRaw)).toInstant()).isEqualTo(sessionExpiry);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE token_hash IN (?, ?)",
                Long.class, raw(original), newRaw)).isZero();
        var jwt = decoder.decode(rotated.path("accessToken").asText());
        assertThat(jwt.getSubject()).isEqualTo(Long.toString(userId));
        assertThat(((Number) jwt.getClaims().get("sid")).longValue()).isEqualTo(sessionId);
        me(rotated, 200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "expired-token", "revoked-session", "expired-session", "blocked-user"})
    void invalidRefreshShouldNotCreateOrConsumeTokens(String scenario) throws Exception {
        String requestToken = raw(original);
        switch (scenario) {
            case "unknown" -> requestToken = tokens.generateToken();
            case "expired-token" -> jdbc.update("""
                    UPDATE refresh_tokens SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                        expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE session_id = ?
                    """, sessionId);
            case "revoked-session" -> jdbc.update("UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP, "
                    + "revoke_reason = 'TEST' WHERE id = ?", sessionId);
            case "expired-session" -> jdbc.update("""
                    UPDATE sessions SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                        expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?
                    """, sessionId);
            case "blocked-user" -> jdbc.update("UPDATE users SET state = 'BLOCKED' WHERE id = ?", userId);
            default -> throw new IllegalArgumentException(scenario);
        }
        MvcResult result = refresh(requestToken, 401);
        assertThat(body(result).path("message").asText()).isEqualTo("Invalid refresh token");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(requestToken, PASSWORD);
        assertThat(tokenCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM refresh_tokens WHERE session_id = ?",
                Boolean.class, sessionId)).isTrue();
    }

    @Test
    void accessExpiryShouldBeCappedByRemainingSessionLifetime() throws Exception {
        jdbc.update("UPDATE sessions SET expires_at = CURRENT_TIMESTAMP + INTERVAL '2 minutes' WHERE id = ?",
                sessionId);
        Instant expiry = sessionExpiry();
        JsonNode rotated = body(refresh(raw(original), 200));
        assertThat(Instant.parse(rotated.path("accessExpiresAt").asText())).isEqualTo(expiry);
        assertThat(Instant.parse(rotated.path("refreshExpiresAt").asText())).isEqualTo(expiry);
        assertThat(sessionExpiry()).isEqualTo(expiry);
        me(rotated, 200);
    }

    @Test
    void reuseShouldCommitRevocationAndRejectLatestTokensButLeaveOtherSessionAlive() throws Exception {
        JsonNode otherSession = login();
        JsonNode rotated = body(refresh(raw(original), 200));
        refresh(raw(original), 401);
        assertRevoked();
        assertThat(tokenCount()).isEqualTo(2);
        me(original, 401);
        me(rotated, 401);
        refresh(raw(rotated), 401);
        me(otherSession, 200);
        refresh(raw(otherSession), 200);
    }

    @Test
    void jwtFailureShouldRollBackConsumptionAndNewToken() throws Exception {
        doThrow(new IllegalStateException("Simulated JWT failure"))
                .when(accessTokens).generateToken(any(), any(), any(), any());
        refresh(raw(original), 500);
        assertThat(tokenCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM refresh_tokens WHERE session_id = ?",
                Boolean.class, sessionId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NULL FROM sessions WHERE id = ?",
                Boolean.class, sessionId)).isTrue();
    }

    @Test
    void concurrentRefreshShouldAllowOneExchangeThenRevokeSession() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<MvcResult> request = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", raw(original)))))
                        .andReturn();
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            MvcResult a = first.get(30, TimeUnit.SECONDS);
            MvcResult b = second.get(30, TimeUnit.SECONDS);
            assertThat(new int[] {a.getResponse().getStatus(), b.getResponse().getStatus()})
                    .containsExactlyInAnyOrder(200, 401);
            assertRevoked();
            assertThat(tokenCount()).isEqualTo(2);
            me(body(a.getResponse().getStatus() == 200 ? a : b), 401);
        }
    }

    private JsonNode login() throws Exception {
        return body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn());
    }

    private MvcResult refresh(String rawToken, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", rawToken))))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
    }

    private void me(JsonNode response, int expectedStatus) throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + response.path("accessToken").asText()))
                .andExpect(status().is(expectedStatus));
    }

    private void assertRevoked() {
        assertThat(jdbc.queryForObject("SELECT revoked_at IS NOT NULL FROM sessions WHERE id = ?",
                Boolean.class, sessionId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM sessions WHERE id = ?",
                String.class, sessionId)).isEqualTo("REFRESH_TOKEN_REUSE");
    }

    private long tokenCount() {
        return jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE session_id = ?", Long.class, sessionId);
    }

    private Instant sessionExpiry() {
        return jdbc.queryForObject("SELECT expires_at FROM sessions WHERE id = ?",
                OffsetDateTime.class, sessionId).toInstant();
    }

    private String raw(JsonNode response) { return response.path("refreshToken").asText(); }
    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
