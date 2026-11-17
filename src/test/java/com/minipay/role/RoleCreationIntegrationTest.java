package com.minipay.role;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.audit.AccessAudit;
import com.minipay.audit.AccessAuditRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.auth.jwt-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class RoleCreationIntegrationTest {
    private static final String PASSWORD = "role-test-password";
    private static final String REQUEST = """
            {"name":"Поддержка","rank":10,"permissionCodes":["PROFILE_READ_ANY"]}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AccessAuditRepository audit;
    private Long actorId;
    private final List<Long> testUserIds = new java.util.ArrayList<>();
    private final List<Long> renameRoleIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : testUserIds) deleteTestUser(id);
        for (Long id : renameRoleIds) {
            jdbc.update("DELETE FROM access_audit WHERE target_role_id = ?", id);
            jdbc.update("DELETE FROM role_assignable_roles WHERE role_id = ? OR assignable_role_id = ?", id, id);
            jdbc.update("DELETE FROM role_grantable_permissions WHERE role_id = ?", id);
            jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", id);
            jdbc.update("DELETE FROM roles WHERE id = ?", id);
        }
        jdbc.update("DELETE FROM access_audit WHERE action = 'ROLE_CREATE' AND actor_user_id = ?", actorId);
        jdbc.update("DELETE FROM role_permissions WHERE role_id IN (SELECT id FROM roles WHERE lower(name) IN ('support', 'поддержка'))");
        jdbc.update("DELETE FROM roles WHERE lower(name) IN ('support', 'поддержка')");
    }

    private void deleteTestUser(Long id) {
        if (id == null) return;
        jdbc.update("DELETE FROM user_extra_permissions WHERE user_id = ?", id);
        jdbc.update("DELETE FROM refresh_tokens WHERE session_id IN (SELECT id FROM sessions WHERE user_id = ?)", id);
        jdbc.update("DELETE FROM sessions WHERE user_id = ?", id);
        jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    @Test
    void superAdminCreatesUnassignedRoleWithAudit() throws Exception {
        var actor = login(true);
        var response = mvc.perform(post("/api/admin/roles")
                        .header("Authorization", "Bearer " + actor.token())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Поддержка"))
                .andExpect(jsonPath("$.rank").value(10))
                .andExpect(jsonPath("$.permissionCodes[0]").value("PROFILE_READ_ANY"))
                .andReturn().getResponse().getContentAsByteArray();
        long roleId = mapper.readTree(response).path("id").asLong();

        assertThat(jdbc.queryForObject("SELECT name FROM roles WHERE id = ?",
                String.class, roleId)).isEqualTo("Поддержка");
        assertThat(jdbc.queryForObject("""
                SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = ?
                """, String.class, roleId)).isEqualTo("PROFILE_READ_ANY");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM access_audit
                WHERE action = 'ROLE_CREATE' AND actor_source = 'USER'
                  AND actor_user_id = ? AND target_role_id = ?
                """, Long.class, actor.id(), roleId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE main_role_id = ?",
                Long.class, roleId)).isZero();
    }

    @Test
    void ordinaryUserCannotCreateRoleOrAuditEvent() throws Exception {
        var actor = login(false);
        mvc.perform(post("/api/admin/roles")
                        .header("Authorization", "Bearer " + actor.token())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles WHERE name = ?",
                Long.class, "Поддержка")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM access_audit WHERE action = 'ROLE_CREATE' AND actor_user_id = ?
                """, Long.class, actor.id())).isZero();
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mvc.perform(post("/api/admin/roles").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
        assertNoCreation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN_TEST_CODE", "WALLET_CREATE_SELF"})
    void invalidPermissionReturnsBadRequest(String code) throws Exception {
        create(login(true), "Support", 10, List.of(code)).andExpect(status().isBadRequest());
        assertNoCreation();
    }

    @Test
    void caseInsensitiveDuplicateReturnsConflict() throws Exception {
        var actor = login(true);
        create(actor, "Support", 10, List.of()).andExpect(status().isCreated());
        create(actor, "support", 10, List.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Role name already exists"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles WHERE lower(name) = 'support'",
                Long.class)).isEqualTo(1L);
        assertThat(auditCount()).isEqualTo(1L);
    }

    @Test
    void rejectsZeroRankAndBlankNameButAcceptsEmptyPermissions() throws Exception {
        var actor = login(true);
        create(actor, "Support", 0, List.of()).andExpect(status().isBadRequest());
        create(actor, "   ", 10, List.of()).andExpect(status().isBadRequest());
        assertNoCreation();
        create(actor, "Support", 10, List.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.permissionCodes").isEmpty());
    }

    @Test
    void auditFailureRollsBackRoleCreation() throws Exception {
        var actor = login(true);
        doThrow(new IllegalStateException("Simulated audit write failure"))
                .when(audit).save(any(AccessAudit.class));
        create(actor, "Support", 10, List.of("PROFILE_READ_ANY"))
                .andExpect(status().isInternalServerError());
        assertNoCreation();
    }

    @Test
    void superAdminListsRolesWithSortedPermissionCodes() throws Exception {
        var actor = login(true);
        var created = create(actor, "Support", 10, List.of("USER_LIST", "PROFILE_READ_ANY"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        int roleId = mapper.readTree(created).path("id").asInt();

        mvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + actor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(roleId))
                .andExpect(jsonPath("$[0].name").value("Support"))
                .andExpect(jsonPath("$[0].rank").value(10))
                .andExpect(jsonPath("$[0].permissionCodes", org.hamcrest.Matchers.contains(
                        "PROFILE_READ_ANY", "USER_LIST")));
    }

    @Test
    void superAdminGetsEmptyListWhenNoRolesExist() throws Exception {
        var actor = login(true);
        mvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + actor.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void ordinaryUserCannotListRoles() throws Exception {
        var actor = login(false);
        mvc.perform(get("/api/admin/roles").header("Authorization", "Bearer " + actor.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listingRolesWithoutTokenReturnsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/roles")).andExpect(status().isUnauthorized());
    }

    @Test
    void renamingRoleUpdatesNameAndWritesAudit() throws Exception {
        var actor = login(true);
        long id = roleForRename(actor, "Support");

        rename(actor, id, " Help Desk ").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Help Desk"))
                .andExpect(jsonPath("$.rank").value(10))
                .andExpect(jsonPath("$.permissionCodes[0]").value("PROFILE_READ_ANY"));

        assertThat(roleName(id)).isEqualTo("Help Desk");
        assertThat(renameAuditCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForMap("""
                SELECT actor_user_id, before_data ->> 'name' AS old_name,
                       after_data ->> 'name' AS new_name
                FROM access_audit WHERE action = 'ROLE_RENAME' AND target_role_id = ?
                """, id)).containsEntry("actor_user_id", actor.id())
                .containsEntry("old_name", "Support")
                .containsEntry("new_name", "Help Desk");
    }

    @Test
    void unchangedNormalizedNameDoesNotWriteAudit() throws Exception {
        var actor = login(true);
        long id = roleForRename(actor, "Support");
        rename(actor, id, " Support ").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Support"));
        assertThat(roleName(id)).isEqualTo("Support");
        assertThat(renameAuditCount(id)).isZero();
    }

    @Test
    void occupiedNameIgnoringCaseReturnsConflictWithoutChanges() throws Exception {
        var actor = login(true);
        long id = roleForRename(actor, "Support");
        long otherId = roleForRename(actor, "Help Desk");
        rename(actor, id, " help desk ").andExpect(status().isConflict());
        assertThat(roleName(id)).isEqualTo("Support");
        assertThat(roleName(otherId)).isEqualTo("Help Desk");
        assertThat(renameAuditCount(id)).isZero();
        assertThat(renameAuditCount(otherId)).isZero();
    }

    @Test
    void ordinaryUserCannotRenameRole() throws Exception {
        var actor = login(false);
        long id = jdbc.queryForObject("INSERT INTO roles (name, rank) VALUES (?, ?) RETURNING id",
                Long.class, "Support", 10);
        renameRoleIds.add(id);
        rename(actor, id, "Help Desk").andExpect(status().isForbidden());
        assertThat(roleName(id)).isEqualTo("Support");
        assertThat(renameAuditCount(id)).isZero();
    }

    @Test
    void renamingMissingRoleReturnsNotFound() throws Exception {
        rename(login(true), Long.MAX_VALUE, "Help Desk").andExpect(status().isNotFound());
    }

    @Test
    void auditFailureRollsBackRename() throws Exception {
        var actor = login(true);
        long id = roleForRename(actor, "Support");
        doThrow(new IllegalStateException("Simulated audit write failure"))
                .when(audit).save(any(AccessAudit.class));

        rename(actor, id, "Help Desk").andExpect(status().isInternalServerError());
        assertThat(roleName(id)).isEqualTo("Support");
        assertThat(renameAuditCount(id)).isZero();
    }

    @Test
    void changedRolePermissionsRevokeEmployeesAccessAndRefreshTokens() throws Exception {
        var admin = login(true);
        long roleId = trackedRole(admin, "Support", List.of("PROFILE_READ_ANY"));
        var employee = login(false, roleId);

        updatePermissions(admin, roleId, List.of("USER_LIST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionCodes[0]").value("USER_LIST"));
        assertThat(permissionUpdateAuditCount(roleId)).isEqualTo(1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + employee.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", employee.refreshToken()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void samePermissionsInDifferentOrderKeepEmployeeSessionAndSkipAudit() throws Exception {
        var admin = login(true);
        long roleId = trackedRole(admin, "Support", List.of("PROFILE_READ_ANY", "USER_LIST"));
        var employee = login(false, roleId);

        updatePermissions(admin, roleId, List.of("USER_LIST", "PROFILE_READ_ANY"))
                .andExpect(status().isOk());
        assertThat(permissionUpdateAuditCount(roleId)).isZero();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + employee.token()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("refreshToken", employee.refreshToken()))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions updatePermissions(
            Actor admin, long roleId, List<String> codes) throws Exception {
        return mvc.perform(put("/api/admin/roles/{id}/permissions", roleId)
                .header("Authorization", "Bearer " + admin.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("permissionCodes", codes))));
    }

    private long permissionUpdateAuditCount(long id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM access_audit WHERE action = 'ROLE_PERMISSIONS_UPDATE' AND target_role_id = ?",
                Long.class, id);
    }

    @Test
    void changedRankRevokesSessionAndAuditsButSameRankDoesNot() throws Exception {
        var admin = login(true);
        long id = trackedRole(admin, "Support", List.of());
        var employee = login(false, id);
        for (int rank : List.of(10, 20)) {
            mvc.perform(patch("/api/admin/roles/{id}/rank", id)
                            .header("Authorization", "Bearer " + admin.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(Map.of("rank", rank))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.rank").value(rank));
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + employee.token()))
                    .andExpect(status().is(rank == 10 ? 200 : 401));
        }
        assertThat(jdbc.queryForMap("""
                SELECT before_data ->> 'rank' AS old_rank, after_data ->> 'rank' AS new_rank
                FROM access_audit WHERE action = 'ROLE_RANK_UPDATE' AND target_role_id = ?
                """, id)).containsEntry("old_rank", "10").containsEntry("new_rank", "20");
    }

    @Test
    void configuredOptionsAreVisibleToEmployeeAndDeleteCleansIncomingLinks() throws Exception {
        var admin = login(true);
        long manager = trackedRole(admin, "Support", List.of());
        long target = trackedRole(admin, "Help Desk", List.of());
        mvc.perform(put("/api/admin/roles/{id}/assignable-roles", manager)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("roleIds", List.of(target)))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/admin/roles/{id}/grantable-permissions", manager)
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("permissionCodes", List.of("USER_LIST")))))
                .andExpect(status().isOk());
        var employee = login(false, manager);
        mvc.perform(get("/api/admin/roles/options").header("Authorization", "Bearer " + employee.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignableRoles[0].name").value("Help Desk"))
                .andExpect(jsonPath("$.grantablePermissionCodes[0]").value("USER_LIST"));
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + employee.token()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/roles/{id}", manager).header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/roles/{id}", target).header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM role_assignable_roles WHERE role_id = ?",
                Long.class, manager)).isZero();
        assertThat(jdbc.queryForList("SELECT action FROM access_audit WHERE target_role_id = ?", String.class, target))
                .contains("ROLE_CREATE", "ROLE_DELETE");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + employee.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerCanAssignGrantRevokeReplaceAndRemoveWithinScope() throws Exception {
        var admin = login(true);
        long managerRole = trackedRole(admin, "Manager", 30,
                List.of("MAIN_ROLE_ASSIGN", "MAIN_ROLE_REMOVE", "EXTRA_PERMISSION_GRANT", "EXTRA_PERMISSION_REVOKE"));
        long firstRole = trackedRole(admin, "Support", List.of("PROFILE_READ_ANY"));
        long nextRole = trackedRole(admin, "Help Desk", List.of());
        configureManager(managerRole, List.of(firstRole, nextRole), admin);
        var manager = login(false, managerRole);
        var employee = login(false);

        assignMainRole(manager, employee, firstRole).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + employee.token()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("refreshToken", employee.refreshToken()))))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT main_role_id FROM users WHERE id = ?", Long.class, employee.id()))
                .isEqualTo(firstRole);

        String path = "/api/admin/users/{userId}/extra-permissions/{code}";
        mvc.perform(put(path, employee.id(), "WALLET_READ_ANY").header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isForbidden());
        mvc.perform(put(path, employee.id(), "PROFILE_READ_ANY").header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isConflict());
        mvc.perform(put(path, employee.id(), "USER_LIST").header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isNoContent());
        mvc.perform(put(path, employee.id(), "USER_LIST").header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isConflict());
        mvc.perform(delete(path, employee.id(), "USER_LIST").header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isNoContent());
        mvc.perform(put(path, employee.id(), "USER_LIST").header("Authorization", "Bearer " + manager.token()))
                .andExpect(status().isNoContent());
        assignMainRole(manager, employee, nextRole).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_extra_permissions WHERE user_id = ?",
                Long.class, employee.id())).isZero();
        mvc.perform(delete("/api/admin/users/{userId}/main-role", employee.id())
                .header("Authorization", "Bearer " + manager.token())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT main_role_id FROM users WHERE id = ?", Long.class, employee.id())).isNull();
        assertThat(jdbc.queryForList("SELECT action FROM access_audit WHERE target_user_id = ? ORDER BY id",
                String.class, employee.id())).containsExactly("MAIN_ROLE_ASSIGN", "EXTRA_PERMISSION_GRANT",
                        "EXTRA_PERMISSION_REVOKE", "EXTRA_PERMISSION_GRANT", "MAIN_ROLE_ASSIGN", "MAIN_ROLE_REMOVE");
    }

    @Test
    void managerCannotChangeEqualHigherOrOutsideZoneEmployees() throws Exception {
        var admin = login(true);
        long managerRole = trackedRole(admin, "Manager", 30, List.of("MAIN_ROLE_ASSIGN"));
        long equal = trackedRole(admin, "Equal", 30, List.of());
        long higher = trackedRole(admin, "Higher", 40, List.of());
        long outside = trackedRole(admin, "Outside", 5, List.of());
        long allowed = trackedRole(admin, "Support", List.of());
        configureManager(managerRole, List.of(equal, higher, allowed), admin);
        var manager = login(false, managerRole);
        for (long role : List.of(equal, higher, outside)) {
            var employee = login(false, role);
            assignMainRole(manager, employee, allowed).andExpect(status().isForbidden());
        }
        var unassigned = login(false);
        for (long role : List.of(equal, higher, outside)) {
            assignMainRole(manager, unassigned, role).andExpect(status().isForbidden());
        }
    }

    private void configureManager(long roleId, List<Long> allowed, Actor admin) throws Exception {
        mvc.perform(put("/api/admin/roles/{id}/assignable-roles", roleId)
                .header("Authorization", "Bearer " + admin.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("roleIds", allowed)))).andExpect(status().isOk());
        mvc.perform(put("/api/admin/roles/{id}/grantable-permissions", roleId)
                .header("Authorization", "Bearer " + admin.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("permissionCodes", List.of("USER_LIST", "PROFILE_READ_ANY")))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions assignMainRole(Actor manager, Actor employee,
            long roleId) throws Exception {
        return mvc.perform(put("/api/admin/users/{userId}/main-role", employee.id())
                .header("Authorization", "Bearer " + manager.token()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("roleId", roleId))));
    }

    @Test
    void superAdminSeesBasePermissionsInCatalogButCannotGrantThem() throws Exception {
        var admin = login(true);
        mvc.perform(get("/api/admin/roles/options").header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionCatalogCodes").value(org.hamcrest.Matchers.hasItems(
                        "WALLET_CREATE_SELF", "USER_LIST")))
                .andExpect(jsonPath("$.grantablePermissionCodes").value(org.hamcrest.Matchers.hasItem("USER_LIST")))
                .andExpect(jsonPath("$.grantablePermissionCodes").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("WALLET_CREATE_SELF"))));
    }

    private long roleForRename(Actor actor, String name) throws Exception {
        return trackedRole(actor, name, List.of("PROFILE_READ_ANY"));
    }

    private long trackedRole(Actor actor, String name, List<String> codes) throws Exception {
        return trackedRole(actor, name, 10, codes);
    }

    private long trackedRole(Actor actor, String name, int rank, List<String> codes) throws Exception {
        var response = create(actor, name, rank, codes)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        long id = mapper.readTree(response).path("id").asLong();
        renameRoleIds.add(id);
        return id;
    }

    private org.springframework.test.web.servlet.ResultActions rename(Actor actor, long id, String name)
            throws Exception {
        return mvc.perform(patch("/api/admin/roles/{id}/name", id)
                .header("Authorization", "Bearer " + actor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", name))));
    }

    private String roleName(long id) {
        return jdbc.queryForObject("SELECT name FROM roles WHERE id = ?", String.class, id);
    }

    private long renameAuditCount(long id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM access_audit WHERE action = 'ROLE_RENAME' AND target_role_id = ?",
                Long.class, id);
    }

    private org.springframework.test.web.servlet.ResultActions create(
            Actor actor, String name, int rank, List<String> codes) throws Exception {
        return mvc.perform(post("/api/admin/roles")
                .header("Authorization", "Bearer " + actor.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("name", name, "rank", rank, "permissionCodes", codes))));
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM access_audit WHERE action = 'ROLE_CREATE' AND actor_user_id = ?",
                Long.class, actorId);
    }

    private void assertNoCreation() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM roles", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_audit WHERE action = 'ROLE_CREATE'",
                Long.class)).isZero();
    }

    private Actor login(boolean admin) throws Exception {
        return login(admin, null);
    }

    private Actor login(boolean admin, Long mainRoleId) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "name", "Test actor", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray();
        long id = mapper.readTree(registration).path("id").asLong();
        testUserIds.add(id);
        if (actorId == null) actorId = id;
        if (mainRoleId != null) {
            jdbc.update("UPDATE users SET main_role_id = ? WHERE id = ?", mainRoleId, id);
        }
        if (admin) {
            jdbc.update("UPDATE users SET system_role = 'SUPER_ADMIN' WHERE id = ?", id);
        }
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var tokens = mapper.readTree(login);
        return new Actor(id, tokens.path("accessToken").asText(), tokens.path("refreshToken").asText());
    }

    private record Actor(long id, String token, String refreshToken) {}
}
