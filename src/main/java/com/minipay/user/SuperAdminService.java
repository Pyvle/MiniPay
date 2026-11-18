package com.minipay.user;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.minipay.audit.AccessAudit;
import com.minipay.audit.AccessAuditRepository;
import com.minipay.audit.ActorSource;
import com.minipay.common.exception.ConflictException;
import com.minipay.common.exception.UserNotFoundException;
import com.minipay.installation.InstallationStateRepository;
import com.minipay.permission.Permission;
import com.minipay.role.RoleRepository;
import com.minipay.session.SessionRevocationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SuperAdminService {
    private static final String ASSIGN_ACTION = "SUPER_ADMIN_ASSIGN";
    private static final String REMOVE_ACTION = "SUPER_ADMIN_REMOVE";

    private final RoleRepository roles;
    private final UserRepository users;
    private final SessionRevocationService sessions;
    private final AccessAuditRepository audit;
    private final InstallationStateRepository installationState;

    public SuperAdminService(RoleRepository roles, UserRepository users,
            SessionRevocationService sessions, AccessAuditRepository audit,
            InstallationStateRepository installationState) {
        this.roles = roles;
        this.users = users;
        this.sessions = sessions;
        this.audit = audit;
        this.installationState = installationState;
    }

    @Transactional
    public void promoteToSuperAdmin(Long actorId, Long targetId) {
        roles.findAllForUpdate();
        List<User> lockedUsers = users.findAllByIdsForUpdate(List.of(actorId, targetId));
        User actor = lockedUsers.stream()
                .filter(user -> actorId.equals(user.getId()))
                .filter(user -> user.getState() == UserState.ACTIVE
                        && user.getSystemRole() == SystemRole.SUPER_ADMIN)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Access denied"));

        User target = lockedUsers.stream()
                .filter(user -> targetId.equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException(targetId));

        if (target.getSystemRole() == SystemRole.SUPER_ADMIN) {
            return;
        }
        if (target.getState() != UserState.ACTIVE) {
            throw new ConflictException("Only active users can be promoted to SUPER_ADMIN");
        }

        var before = snapshot(target);
        target.setMainRole(null);
        target.getExtraPermissions().clear();
        target.setSystemRole(SystemRole.SUPER_ADMIN);

        sessions.revokeAllForUser(targetId, ASSIGN_ACTION);
        audit.save(new AccessAudit(Instant.now(), actor.getId(), ActorSource.USER,
                ASSIGN_ACTION, targetId, null, before, snapshot(target)));
    }

    @Transactional
    public void removeSuperAdmin(Long actorId, Long targetId) {
        installationState.lockInitializedInstallation()
                .orElseThrow(() -> new ConflictException("Installation is not initialized"));
        List<User> lockedUsers = users.findAllByIdsForUpdate(List.of(actorId, targetId));
        User actor = lockedUsers.stream()
                .filter(user -> actorId.equals(user.getId()))
                .filter(user -> user.getState() == UserState.ACTIVE
                        && user.getSystemRole() == SystemRole.SUPER_ADMIN)
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        User target = lockedUsers.stream()
                .filter(user -> targetId.equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException(targetId));

        if (target.getSystemRole() != SystemRole.SUPER_ADMIN) {
            return;
        }
        if (target.getState() == UserState.ACTIVE
                && users.countBySystemRoleAndState(SystemRole.SUPER_ADMIN, UserState.ACTIVE) <= 1) {
            throw new ConflictException("Cannot remove the last active SUPER_ADMIN");
        }

        var before = snapshot(target);
        target.setSystemRole(SystemRole.USER);
        sessions.revokeAllForUser(targetId, REMOVE_ACTION);
        audit.save(new AccessAudit(Instant.now(), actor.getId(), ActorSource.USER,
                REMOVE_ACTION, targetId, null, before, snapshot(target)));
    }

    private static ObjectNode snapshot(User user) {
        var node = JsonNodeFactory.instance.objectNode()
                .put("systemRole", user.getSystemRole().name())
                .put("mainRoleId", user.getMainRole() == null ? null : user.getMainRole().getId());
        var extras = node.putArray("extraPermissionCodes");
        user.getExtraPermissions().stream().map(Permission::getCode).sorted().forEach(extras::add);
        return node;
    }
}
