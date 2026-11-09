package com.minipay.role;

import com.minipay.permission.Permission;
import com.minipay.support.RepositoryTestSupport;
import com.minipay.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RolePermissionRelationsTest extends RepositoryTestSupport {
    @Test
    void rolePermissionsShouldReloadAndBeSharedAcrossRoles() {
        Permission read = permission("TEST_READ");
        Permission write = permission("TEST_WRITE");
        Role first = role("Support");
        Role second = role("Accounting");
        first.getPermissions().add(read);
        first.getPermissions().add(write);
        second.getPermissions().add(read);
        flushAndClear();
        assertThat(entityManager.find(Role.class, first.getId()).getPermissions())
                .extracting(Permission::getCode).containsExactlyInAnyOrder("TEST_READ", "TEST_WRITE");
        assertThat(entityManager.find(Role.class, second.getId()).getPermissions())
                .extracting(Permission::getCode).containsExactly("TEST_READ");
    }

    @Test
    void extraPermissionsShouldReloadAndBeSharedAcrossUsers() {
        Role role = role("Support");
        Permission read = permission("TEST_READ");
        Permission write = permission("TEST_WRITE");
        User first = user("first@example.com");
        User second = user("second@example.com");
        first.setMainRole(role);
        second.setMainRole(role);
        first.getExtraPermissions().add(read);
        first.getExtraPermissions().add(write);
        second.getExtraPermissions().add(read);
        flushAndClear();
        assertThat(entityManager.find(User.class, first.getId()).getExtraPermissions())
                .extracting(Permission::getCode).containsExactlyInAnyOrder("TEST_READ", "TEST_WRITE");
        assertThat(entityManager.find(User.class, second.getId()).getExtraPermissions())
                .extracting(Permission::getCode).containsExactly("TEST_READ");
    }

    @Test
    void assignableRolesShouldReloadAndRemainDirectional() {
        Role manager = role("Manager");
        Role support = role("Support");
        Role accounting = role("Accounting");
        manager.getAssignableRoles().add(support);
        manager.getAssignableRoles().add(accounting);
        flushAndClear();
        assertThat(entityManager.find(Role.class, manager.getId()).getAssignableRoles())
                .extracting(Role::getName).containsExactlyInAnyOrder("Support", "Accounting");
        assertThat(entityManager.find(Role.class, support.getId()).getAssignableRoles()).isEmpty();
        assertThat(entityManager.find(Role.class, accounting.getId()).getAssignableRoles()).isEmpty();
    }

    @Test
    void ownAndGrantablePermissionsShouldBeIndependent() {
        Role role = role("Manager");
        Permission own = permission("TEST_OWN");
        Permission grant = permission("TEST_GRANT");
        Permission otherGrant = permission("TEST_OTHER_GRANT");
        role.getPermissions().add(own);
        role.getGrantablePermissions().add(grant);
        role.getGrantablePermissions().add(otherGrant);
        flushAndClear();
        Role loaded = entityManager.find(Role.class, role.getId());
        assertThat(loaded.getPermissions()).extracting(Permission::getCode).containsExactly("TEST_OWN");
        assertThat(loaded.getGrantablePermissions()).extracting(Permission::getCode)
                .containsExactlyInAnyOrder("TEST_GRANT", "TEST_OTHER_GRANT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"role_permissions", "user_extra_permissions",
            "role_assignable_roles", "role_grantable_permissions"})
    void removingCollectionElementShouldKeepReferencedEntities(String table) {
        Role owner = role("Manager");
        Role target = role("Support");
        Permission permission = permission("TEST_READ");
        User user = user("alice@example.com");
        user.setMainRole(owner);
        switch (table) {
            case "role_permissions" -> owner.getPermissions().add(permission);
            case "user_extra_permissions" -> user.getExtraPermissions().add(permission);
            case "role_assignable_roles" -> owner.getAssignableRoles().add(target);
            case "role_grantable_permissions" -> owner.getGrantablePermissions().add(permission);
            default -> throw new IllegalArgumentException(table);
        }
        flushAndClear();
        Role loadedRole = entityManager.find(Role.class, owner.getId());
        User loadedUser = entityManager.find(User.class, user.getId());
        switch (table) {
            case "role_permissions" -> loadedRole.getPermissions().clear();
            case "user_extra_permissions" -> loadedUser.getExtraPermissions().clear();
            case "role_assignable_roles" -> loadedRole.getAssignableRoles().clear();
            case "role_grantable_permissions" -> loadedRole.getGrantablePermissions().clear();
            default -> throw new IllegalArgumentException(table);
        }
        flushAndClear();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).isZero();
        assertThat(entityManager.find(Role.class, owner.getId())).isNotNull();
        assertThat(entityManager.find(Role.class, target.getId())).isNotNull();
        assertThat(entityManager.find(Permission.class, permission.getId())).isNotNull();
        assertThat(entityManager.find(User.class, user.getId())).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({
            "role_permissions, role_id, permission_id",
            "user_extra_permissions, user_id, permission_id",
            "role_assignable_roles, role_id, assignable_role_id",
            "role_grantable_permissions, role_id, permission_id"
    })
    void duplicatePairShouldBeRejected(String table, String leftColumn, String rightColumn) {
        Role owner = role("Manager");
        Role target = role("Support");
        Permission permission = permission("TEST_READ");
        User user = user("alice@example.com");
        user.setMainRole(owner);
        flushAndClear();
        long left = table.equals("user_extra_permissions") ? user.getId() : owner.getId();
        long right = table.equals("role_assignable_roles") ? target.getId() : permission.getId();
        String sql = "INSERT INTO " + table + " (" + leftColumn + ", " + rightColumn + ") VALUES (?, ?)";
        jdbc.update(sql, left, right);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, left, right));
    }

    @ParameterizedTest
    @CsvSource({
            "role_permissions, role_id, permission_id, true",
            "role_permissions, role_id, permission_id, false",
            "user_extra_permissions, user_id, permission_id, true",
            "user_extra_permissions, user_id, permission_id, false",
            "role_assignable_roles, role_id, assignable_role_id, true",
            "role_assignable_roles, role_id, assignable_role_id, false",
            "role_grantable_permissions, role_id, permission_id, true",
            "role_grantable_permissions, role_id, permission_id, false"
    })
    void missingReferenceOnEitherSideShouldBeRejected(
            String table, String leftColumn, String rightColumn, boolean missingLeft) {
        Role owner = role("Manager");
        Role target = role("Support");
        Permission permission = permission("TEST_READ");
        User user = user("alice@example.com");
        user.setMainRole(owner);
        flushAndClear();
        long left = missingLeft ? -1L : table.equals("user_extra_permissions") ? user.getId() : owner.getId();
        long right = !missingLeft ? -1L : table.equals("role_assignable_roles") ? target.getId() : permission.getId();
        String sql = "INSERT INTO " + table + " (" + leftColumn + ", " + rightColumn + ") VALUES (?, ?)";
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql, left, right));
    }
}
