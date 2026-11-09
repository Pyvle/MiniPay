package com.minipay.auth;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.session.SessionRevocationService;
import com.minipay.support.PostgresTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class SessionRevocationIntegrationTest {
    private static final String PASSWORD = "revocation-test-password-123";
    private final String firstEmail = "revoke-" + UUID.randomUUID() + "@example.com";
    private final String secondEmail = "revoke-" + UUID.randomUUID() + "@example.com";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtDecoder decoder;
    @Autowired private SessionRevocationService revocations;

    @Test
    void revokeAllShouldRejectBothTokenPairsAndLeaveOtherUserUnaffected() throws Exception {
        long firstUserId = register(firstEmail);
        long secondUserId = register(secondEmail);
        JsonNode firstSession = login(firstEmail);
        JsonNode secondSession = login(firstEmail);
        JsonNode unrelatedSession = login(secondEmail);

        assertThat(sessionId(firstSession)).isNotEqualTo(sessionId(secondSession));
        me(firstSession).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(firstUserId));
        me(secondSession).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(firstUserId));

        revocations.revokeAllForUser(firstUserId, "ACCESS_CHANGED");

        for (JsonNode session : new JsonNode[] {firstSession, secondSession}) {
            me(session).andExpect(status().isUnauthorized());
            refresh(session).andExpect(status().isUnauthorized());
        }
        me(unrelatedSession).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(secondUserId));
        JsonNode rotated = body(refresh(unrelatedSession).andExpect(status().isOk()));
        me(rotated).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(secondUserId));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM refresh_tokens WHERE session_id IN "
                + "(SELECT s.id FROM sessions s JOIN users u ON u.id = s.user_id WHERE u.email IN (?, ?))",
                firstEmail, secondEmail);
        jdbc.update("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE email IN (?, ?))",
                firstEmail, secondEmail);
        jdbc.update("DELETE FROM users WHERE email IN (?, ?)", firstEmail, secondEmail);
    }

    private long register(String email) throws Exception {
        return body(postJson("/api/auth/register", Map.of(
                "name", "Revocation Test", "email", email, "password", PASSWORD))
                .andExpect(status().isCreated())).path("id").longValue();
    }

    private JsonNode login(String email) throws Exception {
        return body(postJson("/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(JsonNode pair) throws Exception {
        return postJson("/api/auth/refresh", Map.of("refreshToken", pair.path("refreshToken").asText()));
    }

    private ResultActions me(JsonNode pair) throws Exception {
        return mvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + pair.path("accessToken").asText()));
    }

    private ResultActions postJson(String path, Map<String, String> data) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(data)));
    }

    private long sessionId(JsonNode pair) {
        return ((Number) decoder.decode(pair.path("accessToken").asText()).getClaims().get("sid")).longValue();
    }

    private JsonNode body(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }
}
