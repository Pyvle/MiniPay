package com.minipay.role;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minipay.audit.AccessAudit;
import com.minipay.audit.AccessAuditRepository;
import com.minipay.audit.ActorSource;
import com.minipay.common.exception.BadRequestException;
import com.minipay.common.exception.ConflictException;
import com.minipay.common.exception.RoleNotFoundException;
import com.minipay.common.exception.UserNotFoundException;
import com.minipay.permission.Permission;
import com.minipay.permission.PermissionCode;
import com.minipay.permission.PermissionRepository;
import com.minipay.session.SessionRevocationService;
import com.minipay.user.SystemRole;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.user.UserState;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserRoleAssignmentService {
    private final RoleRepository roles;
    private final UserRepository users;
    private final PermissionRepository permissions;
    private final SessionRevocationService sessions;
    private final AccessAuditRepository audit;

    public UserRoleAssignmentService(RoleRepository roles, UserRepository users,
            PermissionRepository permissions, SessionRevocationService sessions,
            AccessAuditRepository audit) {
        this.roles = roles;
        this.users = users;
        this.permissions = permissions;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    public void assignMainRole(Long actorId, Long userId, Long roleId) {
        var change = authorize(actorId, userId, PermissionCode.MAIN_ROLE_ASSIGN, true);
        Role next = change.roles().stream().filter(r -> r.getId().equals(roleId)).findFirst()
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        if (!isSuperAdmin(change.actor()) && !canManageRole(change.actor().getMainRole(), next)) {
            throw denied();
        }
        setMainRole(change, next, PermissionCode.MAIN_ROLE_ASSIGN);
    }

    @Transactional
    public void removeMainRole(Long actorId, Long userId) {
        var change = authorize(actorId, userId, PermissionCode.MAIN_ROLE_REMOVE, false);
        setMainRole(change, null, PermissionCode.MAIN_ROLE_REMOVE);
    }

    @Transactional
    public void grantExtraPermission(Long actorId, Long userId, PermissionCode code) {
        var change = authorize(actorId, userId, PermissionCode.EXTRA_PERMISSION_GRANT, false);
        User target = change.target();
        Role main = requireMainRole(target);
        if (!code.isAssignableToServiceRole()) {
            throw new BadRequestException("Permission cannot be assigned separately");
        }
        if (!isSuperAdmin(change.actor())
                && !contains(change.actor().getMainRole().getGrantablePermissions(), code)) {
            throw denied();
        }
        if (contains(main.getPermissions(), code) || contains(target.getExtraPermissions(), code)) {
            throw new ConflictException("Permission already assigned");
        }
        Permission permission = permissions.findByCode(code.name())
                .orElseThrow(() -> new IllegalStateException("Permission missing from catalog"));
        var before = snapshot(target);
        target.getExtraPermissions().add(permission);
        record(change, PermissionCode.EXTRA_PERMISSION_GRANT, main, before);
    }

    @Transactional
    public void revokeExtraPermission(Long actorId, Long userId, PermissionCode code) {
        var change = authorize(actorId, userId, PermissionCode.EXTRA_PERMISSION_REVOKE, false);
        User target = change.target();
        Role main = requireMainRole(target);
        var before = snapshot(target);
        if (target.getExtraPermissions().removeIf(p -> p.getCode().equals(code.name()))) {
            record(change, PermissionCode.EXTRA_PERMISSION_REVOKE, main, before);
        }
    }

    private Change authorize(Long actorId, Long userId, PermissionCode action, boolean allowUnassigned) {
        // Same ordering as role configuration: roles, then users, then sessions.
        var lockedRoles = roles.findAllForUpdate();
        var lockedUsers = users.findAllByIdsForUpdate(List.of(actorId, userId));
        User actor = lockedUsers.stream().filter(u -> u.getId().equals(actorId))
                .findFirst().orElseThrow(UserRoleAssignmentService::denied);
        boolean superAdmin = isSuperAdmin(actor);
        Role actingRole = actor.getMainRole();
        if (actor.getState() != UserState.ACTIVE || (!superAdmin && (actingRole == null
                || !(contains(actingRole.getPermissions(), action)
                        || contains(actor.getExtraPermissions(), action))))) {
            throw denied();
        }
        User target = lockedUsers.stream().filter(u -> u.getId().equals(userId))
                .findFirst().orElseThrow(() -> new UserNotFoundException(userId));
        if (target.getSystemRole() != SystemRole.USER || (!superAdmin
                && (target.getMainRole() == null ? !allowUnassigned : !canManageRole(actingRole, target.getMainRole())))) {
            throw denied();
        }
        return new Change(actor, target, lockedRoles);
    }

    private void setMainRole(Change change, Role next, PermissionCode action) {
        User target = change.target();
        Role previous = target.getMainRole();
        if (Objects.equals(roleId(previous), roleId(next))) return;
        var before = snapshot(target);
        target.setMainRole(next);
        target.getExtraPermissions().clear();
        record(change, action, next == null ? previous : next, before);
    }

    private void record(Change change, PermissionCode action, Role role, ObjectNode before) {
        Long userId = change.target().getId();
        sessions.revokeAllForUser(userId, action.name());
        audit.save(new AccessAudit(Instant.now(), change.actor().getId(), ActorSource.USER,
                action.name(), userId, roleId(role), before, snapshot(change.target())));
    }

    private static ObjectNode snapshot(User user) {
        var node = JsonNodeFactory.instance.objectNode().put("mainRoleId", roleId(user.getMainRole()));
        var extras = node.putArray("extraPermissionCodes");
        user.getExtraPermissions().stream().map(Permission::getCode).sorted().forEach(extras::add);
        return node;
    }

    private static boolean canManageRole(Role manager, Role target) {
        return manager.getRank() > target.getRank()
                && manager.getAssignableRoles().stream().anyMatch(r -> r.getId().equals(target.getId()));
    }

    private static boolean contains(Iterable<Permission> permissions, PermissionCode code) {
        for (Permission permission : permissions) {
            if (permission.getCode().equals(code.name())) return true;
        }
        return false;
    }

    private static Role requireMainRole(User target) {
        if (target.getMainRole() == null) throw new ConflictException("User has no main role");
        return target.getMainRole();
    }

    private static Long roleId(Role role) {
        return role == null ? null : role.getId();
    }

    private static boolean isSuperAdmin(User user) {
        return user.getSystemRole() == SystemRole.SUPER_ADMIN;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Access denied");
    }

    private record Change(User actor, User target, List<Role> roles) {}
}
