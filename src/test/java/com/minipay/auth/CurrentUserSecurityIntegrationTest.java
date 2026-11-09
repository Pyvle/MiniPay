package com.minipay.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.auth.token.AccessTokenService;
import com.minipay.config.JwtConfig;
import com.minipay.support.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.auth.session-ttl=7d",
        "app.auth.access-token-ttl=15m",
        // Public test-only key; never reads the JWT secret from .env.
        "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class CurrentUserSecurityIntegrationTest {
    private static final String PASSWORD = "test-password-123";
    private static final String ME = "/api/auth/me";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AccessTokenService accessTokenService;

    private String email;
    private Long userId;
    private Long sessionId;
    private String accessToken;
    private String refreshToken;
    private String passwordHash;

    @BeforeEach
    void registerAndLogInThroughRealHttpSecurityChain() throws Exception {
        email = "me-" + UUID.randomUUID() + "@example.com";
        String registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Alice", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        userId = objectMapper.readTree(registration).path("id").longValue();

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(login);
        accessToken = body.path("accessToken").asText();
        refreshToken = body.path("refreshToken").asText();
        assertThat(accessToken).isNotBlank();
        sessionId = jdbc.queryForObject("SELECT id FROM sessions WHERE user_id = ?", Long.class, userId);
        passwordHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }

    @AfterEach
    void removeOnlyThisTestsAccount() {
        if (email == null) {
            return;
        }
        jdbc.update("DELETE FROM refresh_tokens WHERE session_id IN "
                + "(SELECT s.id FROM sessions s JOIN users u ON u.id = s.user_id WHERE u.email = ?)", email);
        jdbc.update("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
        jdbc.update("DELETE FROM users WHERE email = ?", email);
    }

    @Test
    void registeredAndLoggedInUserShouldReceiveOnlyTheirPublicProfile() throws Exception {
        String response = mockMvc.perform(me(accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(response).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "name", "email", "createdAt");
        assertThat(response).doesNotContain(PASSWORD, passwordHash, accessToken, refreshToken);
    }

    @Test
    void missingTokenShouldReturnSafeUnauthorizedResponse() throws Exception {
        unauthorized(null);
    }

    @Test
    void damagedJwtShouldReturnSafeUnauthorizedResponse() throws Exception {
        unauthorized("not-a-valid-jwt");
    }

    @Test
    void tokenSignedByAnotherKeyShouldReturnSafeUnauthorizedResponse() throws Exception {
        byte[] otherKey = "different-test-key-0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        var otherEncoder = new JwtConfig().jwtEncoder(new SecretKeySpec(otherKey, "HmacSHA256"));
        var otherService = new AccessTokenService(otherEncoder);
        Instant now = Instant.now();
        unauthorized(otherService.generateToken(userId, sessionId, now, now.plusSeconds(900)));
    }

    @Test
    void jwtExpiredBeyondThirtySecondToleranceShouldBeRejected() throws Exception {
        Instant now = Instant.now();
        unauthorized(accessTokenService.generateToken(
                userId, sessionId, now.minusSeconds(900), now.minusSeconds(120)));
    }

    @Test
    void missingSessionShouldBeRejectedEvenWithValidJwtSignature() throws Exception {
        jdbc.update("DELETE FROM refresh_tokens WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM sessions WHERE id = ?", sessionId);
        unauthorized(accessToken);
    }

    @Test
    void revokedSessionShouldBeRejectedEvenWithUnexpiredJwt() throws Exception {
        jdbc.update("UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP, revoke_reason = 'LOGOUT' WHERE id = ?",
                sessionId);
        unauthorized(accessToken);
    }

    @Test
    void expiredSessionShouldBeRejectedEvenWithUnexpiredJwt() throws Exception {
        jdbc.update("""
                UPDATE sessions SET created_at = CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    expires_at = CURRENT_TIMESTAMP - INTERVAL '2 minutes' WHERE id = ?
                """, sessionId);
        unauthorized(accessToken);
    }

    @Test
    void subjectDifferentFromSessionOwnerShouldBeRejected() throws Exception {
        Instant now = Instant.now();
        unauthorized(accessTokenService.generateToken(userId + 1, sessionId, now, now.plusSeconds(900)));
    }

    @Test
    void userBlockedAfterLoginShouldLoseAccessImmediately() throws Exception {
        jdbc.update("UPDATE users SET state = 'BLOCKED' WHERE id = ?", userId);
        unauthorized(accessToken);
    }

    private MockHttpServletRequestBuilder me(String token) {
        var request = get(ME);
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return request;
    }

    private void unauthorized(String token) throws Exception {
        String response = mockMvc.perform(me(token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().encoding(StandardCharsets.UTF_8.name()))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required or invalid"))
                .andExpect(jsonPath("$.path").value(ME))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        assertThat(body.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path");
        Instant.parse(body.path("timestamp").asText());
        assertThat(response).doesNotContain(PASSWORD, passwordHash, accessToken, refreshToken);
        if (token != null) {
            assertThat(response).doesNotContain(token);
        }
    }
}
