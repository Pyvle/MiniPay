package com.minipay.auth;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.user.SystemRole;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.user.UserState;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class RegistrationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void registrationShouldPersistSafeDefaultsAndReturnOnlyPublicFields() throws Exception {
        String password = "test-password-123";
        String response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("Alice@Example.COM", password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "name", "email", "createdAt");
        User user = userRepository.findByEmail("alice@example.com").orElseThrow();
        assertThat(user.getId()).isEqualTo(body.get("id").longValue());
        assertThat(user.getPasswordHash()).isNotBlank().isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, user.getPasswordHash())).isTrue();
        assertThat(user.getSystemRole()).isEqualTo(SystemRole.USER);
        assertThat(user.getState()).isEqualTo(UserState.ACTIVE);
        assertThat(user.getMainRole()).isNull();
        assertThat(user.getExtraPermissions()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"invalid-email,12345678", "alice@example.com,1234567"})
    void invalidRegistrationShouldNotCreateUser(String email, String password) throws Exception {
        long count = userRepository.count();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(email, password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertThat(userRepository.count()).isEqualTo(count);
    }

    @Test
    void duplicateEmailShouldReturnConflictAfterNormalization() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("alice@example.com", "12345678")))
                .andExpect(status().isCreated());
        long count = userRepository.count();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("ALICE@EXAMPLE.COM", "87654321")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(userRepository.count()).isEqualTo(count);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser(username = "1")
    void oldUserEndpointShouldRejectCreation() throws Exception {
        long count = userRepository.count();
        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request("legacy@example.com", "12345678")))
                .andExpect(status().isMethodNotAllowed());
        assertThat(userRepository.count()).isEqualTo(count);
    }

    private String request(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "name", "Alice", "email", email, "password", password));
    }
}
