package com.minipay.auth;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class LogoutIntegrationTest {
    private static final String PASSWORD = "logout-test-password-123";
    private static final String LOGOUT = "/api/auth/logout";
    private static final String REFRESH = "/api/auth/refresh";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtDecoder decoder;
    @Autowired private RefreshTokenService tokens;

    private String email;

    @BeforeEach
    void register() throws Exception {
        email = "logout-" + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "name", "Logout Test", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE session_id IN "
                + "(SELECT s.id FROM sessions s JOIN users u ON u.id = s.user_id WHERE u.email = ?)", email);
        jdbc.update("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
        jdbc.update("DELETE FROM users WHERE email = ?", email);
    }

    @Test
    void logoutShouldRevokeOnlyThePresentedSession() throws Exception {
        JsonNode current = login();
        JsonNode other = login();

        logout(current);

        assertRevoked(current);
        assertTokensRejected(current);
        me(other).andExpect(status().isOk());
        tokenRequest(REFRESH, raw(other)).andExpect(status().isOk());
    }

    @Test
    void repeatedLogoutShouldKeepOriginalRevocation() throws Exception {
        JsonNode current = login();
        logout(current);
        OffsetDateTime revokedAt = revokedAt(current);
        assertThat(revokedAt).isNotNull();

        logout(current);

        assertThat(revokedAt(current)).isEqualTo(revokedAt);
        assertRevoked(current);
    }

    @Test
    void unknownTokenShouldReturnNoContentWithoutRevokingAnotherSession() throws Exception {
        JsonNode current = login();
        tokenRequest(LOGOUT, tokens.generateToken())
                .andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(revokedAt(current)).isNull();
        me(current).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"refreshToken\":\"\"}", "{\"refreshToken\":\"   \"}"})
    void missingOrBlankTokenShouldReturnBadRequest(String body) throws Exception {
        mvc.perform(post(LOGOUT).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutWithUsedRefreshTokenShouldRejectTheRotatedPair() throws Exception {
        JsonNode original = login();
        JsonNode rotated = body(tokenRequest(REFRESH, raw(original))
                .andExpect(status().isOk()).andReturn());

        logout(original);

        assertRevoked(original);
        assertTokensRejected(rotated);
    }

    @Test
    void concurrentRefreshAndLogoutShouldLeaveSessionRevoked() throws Exception {
        JsonNode original = login();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var refreshing = executor.submit(() -> concurrentRequest(REFRESH, raw(original), ready, start));
            var loggingOut = executor.submit(() -> concurrentRequest(LOGOUT, raw(original), ready, start));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }

            MvcResult logout = loggingOut.get(30, TimeUnit.SECONDS);
            MvcResult refresh = refreshing.get(30, TimeUnit.SECONDS);
            assertThat(logout.getResponse().getStatus()).isEqualTo(204);
            assertThat(logout.getResponse().getContentAsString()).isEmpty();
            assertThat(refresh.getResponse().getStatus()).isIn(200, 401);
            assertRevoked(original);
            assertTokensRejected(original);
            if (refresh.getResponse().getStatus() == 200) {
                assertTokensRejected(body(refresh));
            }
        }
    }

    private MvcResult concurrentRequest(String path, String rawToken, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent request start timed out");
        }
        return tokenRequest(path, rawToken).andReturn();
    }

    private JsonNode login() throws Exception {
        return body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn());
    }

    private void logout(JsonNode pair) throws Exception {
        tokenRequest(LOGOUT, raw(pair)).andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    private void assertTokensRejected(JsonNode pair) throws Exception {
        me(pair).andExpect(status().isUnauthorized());
        tokenRequest(REFRESH, raw(pair)).andExpect(status().isUnauthorized());
    }

    private ResultActions me(JsonNode pair) throws Exception {
        return mvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pair.path("accessToken").asText()));
    }

    private ResultActions tokenRequest(String path, String rawToken) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", rawToken))));
    }

    private void assertRevoked(JsonNode pair) {
        assertThat(revokedAt(pair)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT revoke_reason FROM sessions WHERE id = ?",
                String.class, sessionId(pair))).isEqualTo("LOGOUT");
    }

    private OffsetDateTime revokedAt(JsonNode pair) {
        return jdbc.queryForObject("SELECT revoked_at FROM sessions WHERE id = ?",
                OffsetDateTime.class, sessionId(pair));
    }

    private long sessionId(JsonNode pair) {
        return ((Number) decoder.decode(pair.path("accessToken").asText()).getClaims().get("sid")).longValue();
    }

    private String raw(JsonNode pair) {
        return pair.path("refreshToken").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }
}
