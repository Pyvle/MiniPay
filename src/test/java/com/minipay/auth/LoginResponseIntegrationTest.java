package com.minipay.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.auth.token.AccessTokenService;
import com.minipay.session.RefreshTokenService;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.auth.session-ttl=7d",
        "app.auth.access-token-ttl=15m",
        // Public test-only key: Base64 of the 32 ASCII bytes below.
        "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class LoginResponseIntegrationTest {
    private static final String PASSWORD = "test-password-123";
    private static final byte[] TEST_KEY =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuthService authService;
    @Autowired private RefreshTokenService refreshTokenService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AccessTokenService accessTokenService;

    private User user;

    @BeforeEach
    void createAccount() {
        user = authService.register("Alice", "login-" + UUID.randomUUID() + "@example.com", PASSWORD);
    }

    @AfterEach
    void removeAccountAndItsSessions() {
        if (user == null) {
            return;
        }
        jdbc.update("DELETE FROM refresh_tokens WHERE session_id IN "
                + "(SELECT id FROM sessions WHERE user_id = ?)", user.getId());
        jdbc.update("DELETE FROM sessions WHERE user_id = ?", user.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", user.getId());
    }

    @Test
    void successfulLoginShouldReturnBothTokensAndBearerType() throws Exception {
        JsonNode body = login();
        assertThat(body.path("accessToken").asText()).isNotBlank();
        assertThat(body.path("refreshToken").asText()).isNotBlank();
        assertThat(body.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(Instant.parse(body.path("refreshExpiresAt").asText()))
                .isAfter(Instant.parse(body.path("accessExpiresAt").asText()));
    }

    @Test
    void returnedRefreshTokenShouldMatchOnlyHashStoredForSession() throws Exception {
        JsonNode body = login();
        String rawToken = body.path("refreshToken").asText();
        String storedHash = jdbc.queryForObject("""
                SELECT rt.token_hash FROM refresh_tokens rt
                JOIN sessions s ON s.id = rt.session_id WHERE s.user_id = ?
                """, String.class, user.getId());
        assertThat(storedHash).isEqualTo(refreshTokenService.hashToken(rawToken))
                .isNotEqualTo(rawToken);
        Instant refreshExpiry = jdbc.queryForObject("""
                SELECT rt.expires_at FROM refresh_tokens rt
                JOIN sessions s ON s.id = rt.session_id WHERE s.user_id = ?
                """, (rs, row) -> rs.getTimestamp(1).toInstant(), user.getId());
        assertThat(refreshExpiry).isCloseTo(
                Instant.parse(body.path("refreshExpiresAt").asText()), within(1, ChronoUnit.MICROS));
    }

    @Test
    void signedJwtShouldIdentifyUserSessionAndValidityPeriod() throws Exception {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JsonNode body = login();
        Instant after = Instant.now();
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(TEST_KEY, "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        var jwt = decoder.decode(body.path("accessToken").asText());
        Long sessionId = jdbc.queryForObject(
                "SELECT id FROM sessions WHERE user_id = ?", Long.class, user.getId());
        Instant sessionCreated = jdbc.queryForObject(
                "SELECT created_at FROM sessions WHERE id = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), sessionId);

        assertThat(jwt.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(((Number) jwt.getClaim("sid")).longValue()).isEqualTo(sessionId);
        assertThat(jwt.getIssuedAt()).isBetween(before, after)
                .isEqualTo(sessionCreated.truncatedTo(ChronoUnit.SECONDS));
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofMinutes(15)))
                .isEqualTo(Instant.parse(body.path("accessExpiresAt").asText())
                        .truncatedTo(ChronoUnit.SECONDS));
    }

    @Test
    void responseShouldContainOnlyPublicTokenContractFields() throws Exception {
        JsonNode body = login();
        assertThat(body.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("accessToken", "refreshToken", "tokenType",
                        "accessExpiresAt", "refreshExpiresAt");
        assertThat(body.toString()).doesNotContain(PASSWORD, user.getPasswordHash());
    }

    @Test
    void jwtIssuanceFailureShouldRollBackSessionAndRefreshToken() throws Exception {
        // No test transaction: observe the real rollback of AuthService.login.
        long previousTokenCount = jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Long.class);
        doAnswer(invocation -> {
            Long sessionId = invocation.getArgument(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions WHERE id = ?",
                    Long.class, sessionId)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE session_id = ?",
                    Long.class, sessionId)).isEqualTo(1L);
            throw new JwtEncodingException("Simulated signing failure");
        }).when(accessTokenService).generateToken(anyLong(), anyLong(), any(Instant.class), any(Instant.class));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(request()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessions WHERE user_id = ?",
                Long.class, user.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens", Long.class))
                .isEqualTo(previousTokenCount);
    }

    private JsonNode login() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String request() throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD));
    }
}
