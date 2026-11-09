package com.minipay.auth;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class RegistrationConstraintHandlingTest extends com.minipay.support.MvcSecurityTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.minipay.user.UserService userService;

    @ParameterizedTest
    @CsvSource({
            "23505, users_email_key, 409, Conflict, Email already registered",
            "23505, another_unique_key, 500, Internal Server Error, Unexpected server error",
            "23502, users_email_key, 500, Internal Server Error, Unexpected server error"
    })
    void registrationShouldClassifyDatabaseConstraintFailure(
            String sqlState, String constraintName, int expectedStatus,
            String expectedError, String expectedMessage) throws Exception {
        SQLException sqlException = new SQLException("Internal database details", sqlState);
        ConstraintViolationException violation = new ConstraintViolationException(
                "Constraint failure", sqlException, constraintName);
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "Insert failed", new RuntimeException("Persistence wrapper", violation));

        when(authService.register("Alice", "alice@example.com", "12345678"))
                .thenThrow(exception);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Alice","email":"alice@example.com","password":"12345678"}
                        """))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.error").value(expectedError))
                .andExpect(jsonPath("$.message").value(expectedMessage))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
