package com.minipay.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.minipay.permission.PermissionCode;
import com.minipay.permission.PermissionRepository;
import com.minipay.role.Role;
import com.minipay.role.RoleRepository;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.user.SystemRole;
import com.minipay.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
@Transactional
class AccessAuditIntegrationTest {
    private static final String AUDIT_PATH = "/api/admin/access-audit";
    private static final String PASSWORD = "audit-test-password";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private AccessAuditRepository audit;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;

    @Test
    void superAdminReadsAuditInStableDescendingOrder() throws Exception {
        String authorization = authorizationFor(SystemRole.SUPER_ADMIN, null);
        Instant timestamp = Instant.parse("2026-01-01T12:00:00Z");
        var first = saveEvent(timestamp, "ROLE_CREATE");
        var second = saveEvent(timestamp, "ROLE_RENAME");
        var oldest = saveEvent(timestamp.minusSeconds(1), "ROLE_DELETE");

        mvc.perform(get(AUDIT_PATH).param("size", "1").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.content[0].action").value("ROLE_RENAME"))
                .andExpect(jsonPath("$.content[0].beforeData.name").value("Support"))
                .andExpect(jsonPath("$.content[0].afterData.name").value("Help Desk"));

        mvc.perform(get(AUDIT_PATH).param("page", "1").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(first.getId()));

        mvc.perform(get(AUDIT_PATH).param("page", "2").param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(oldest.getId()));
    }

    @Test
    void employeeWithAuditReadCanReadJournal() throws Exception {
        String authorization = authorizationFor(SystemRole.USER, PermissionCode.ACCESS_AUDIT_READ);
        mvc.perform(get(AUDIT_PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void employeeWithUnrelatedPermissionIsForbidden() throws Exception {
        String authorization = authorizationFor(SystemRole.USER, PermissionCode.PROFILE_READ_ANY);
        mvc.perform(get(AUDIT_PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mvc.perform(get(AUDIT_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101"})
    void invalidPaginationIsRejected(int page, int size) throws Exception {
        String authorization = authorizationFor(SystemRole.SUPER_ADMIN, null);
        mvc.perform(get(AUDIT_PATH).param("page", Integer.toString(page)).param("size", Integer.toString(size))
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private AccessAudit saveEvent(Instant timestamp, String action) {
        return audit.saveAndFlush(new AccessAudit(timestamp, 1L, ActorSource.USER, action, null, 1L,
                JsonNodeFactory.instance.objectNode().put("name", "Support"),
                JsonNodeFactory.instance.objectNode().put("name", "Help Desk")));
    }

    private String authorizationFor(SystemRole systemRole, PermissionCode permission) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "name", "Audit reader", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn();
        long userId = mapper.readTree(registration.getResponse().getContentAsByteArray()).path("id").asLong();
        var user = users.findById(userId).orElseThrow();
        user.setSystemRole(systemRole);
        if (permission != null) {
            var role = new Role("Audit reader", 1);
            role.getPermissions().add(permissions.findByCode(permission.name()).orElseThrow());
            user.setMainRole(roles.save(role));
        }
        users.flush();

        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(login.getResponse().getContentAsByteArray()).path("accessToken").asText();
        return "Bearer " + token;
    }
}
