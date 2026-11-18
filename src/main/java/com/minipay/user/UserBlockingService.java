package com.minipay.user;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.minipay.audit.AccessAudit;
import com.minipay.audit.AccessAuditRepository;
import com.minipay.audit.ActorSource;
import com.minipay.common.exception.ConflictException;
import com.minipay.installation.InstallationStateRepository;
import com.minipay.permission.PermissionCode;
import com.minipay.role.Role;
import com.minipay.role.RoleRepository;

import com.minipay.session.SessionRevocationService;

import java.time.Instant;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserBlockingService {
    private final SessionRevocationService sessionRevocationService;
    private final AccessAuditRepository accessAuditRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final InstallationStateRepository installationState;

    public UserBlockingService(RoleRepository roleRepository, UserRepository userRepository,
            AccessAuditRepository accessAuditRepository, SessionRevocationService sessionRevocationService,
            InstallationStateRepository installationState) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.accessAuditRepository = accessAuditRepository;
        this.sessionRevocationService = sessionRevocationService;
        this.installationState = installationState;

    }

    @Transactional
    public void block(Long actorId, Long targetId) {
        changeState(actorId, targetId, UserState.BLOCKED, PermissionCode.USER_BLOCK);
    }

    @Transactional
    public void unblock(Long actorId, Long targetId) {
        changeState(actorId, targetId, UserState.ACTIVE, PermissionCode.USER_UNBLOCK);
    }

    private void changeState(Long actorId, Long targetId,
            UserState newState, PermissionCode permission) {
        roleRepository.findAllForUpdate();
        boolean installationLocked = newState == UserState.BLOCKED
                && installationState.lockInitializedInstallation().isPresent();

        List<User> lockUsers = userRepository.findAllByIdsForUpdate(List.of(actorId, targetId));

        User actor = lockUsers.stream()
                .filter(user -> actorId.equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Access denied"));

        User target = lockUsers.stream()
                .filter(user -> targetId.equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Access denied"));

        requirePermission(actor, target, permission);

        UserState previousState = target.getState();
        if (previousState == newState) {
            return;
        }
        if (newState == UserState.BLOCKED && target.getSystemRole() == SystemRole.SUPER_ADMIN) {
            if (!installationLocked) {
                throw new ConflictException("Installation is not initialized");
            }
            if (userRepository.countBySystemRoleAndState(SystemRole.SUPER_ADMIN, UserState.ACTIVE) <= 1) {
                throw new ConflictException("Cannot block the last active SUPER_ADMIN");
            }
        }
        target.setState(newState);
        String action = permission.name();

        if (newState == UserState.BLOCKED) {
            sessionRevocationService.revokeAllForUser(target.getId(), action);
        }

        var json = JsonNodeFactory.instance;
        accessAuditRepository.save(new AccessAudit(
                Instant.now(), actor.getId(), ActorSource.USER, action,
                target.getId(), null,
                json.objectNode().put("state", previousState.name()),
                json.objectNode().put("state", newState.name())));
    }

    private void requirePermission(User actor, User target,
            PermissionCode permission) {
        if (actor.getState() != UserState.ACTIVE) {
            throw new AccessDeniedException("Access denied");
        }
        if (actor.getSystemRole() == SystemRole.SUPER_ADMIN) {
            return;
        }
        if (target.getSystemRole() != SystemRole.USER) {
            throw new AccessDeniedException("Access denied");
        }

        Role actorRole = actor.getMainRole();
        Role targetRole = target.getMainRole();
        if (actorRole == null || targetRole == null) {
            throw new AccessDeniedException("Access denied");
        }

        String code = permission.name();
        boolean hasPermission = actorRole.getPermissions().stream()
                .anyMatch(actorPermission -> code.equals(actorPermission.getCode()))
                || actor.getExtraPermissions().stream()
                        .anyMatch(actorPermission -> code.equals(actorPermission.getCode()));

        boolean inScope = actorRole.getAssignableRoles().stream()
                .anyMatch(role -> targetRole.getId().equals(role.getId()));

        if (!hasPermission || !inScope || actorRole.getRank() <= targetRole.getRank()) {
            throw new AccessDeniedException("Access denied");
        }
    }
}
