package com.minipay.auth;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.minipay.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class RegistrationConcurrencyIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PasswordEncoder passwordEncoder;

    @Test
    void concurrentRegistrationsShouldCreateOneUserAndReturnConflictForTheOther() throws Exception {
        String email = "concurrent-registration@example.com";
        String password = "concurrent-password-123";
        String body = """
                {"name":"Alice","email":"%s","password":"%s"}
                """.formatted(email, password);
        CyclicBarrier bothPassedEmailCheck = new CyclicBarrier(2);
        
        doAnswer(invocation -> {
            bothPassedEmailCheck.await(10, TimeUnit.SECONDS);
            return invocation.callRealMethod();
        }).when(passwordEncoder).encode(password);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> register = () -> mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andReturn().getResponse().getStatus();
            var first = executor.submit(register);
            var second = executor.submit(register);

            assertThat(new int[] {first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM users WHERE email = ?", Long.class, email))
                    .isEqualTo(1L);
        } finally {
            jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        }
    }
}
