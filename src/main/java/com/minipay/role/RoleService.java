package com.minipay.role;

import com.minipay.permission.PermissionRepository;
import com.minipay.permission.Permission;
import com.minipay.audit.AccessAudit;
import com.minipay.audit.AccessAuditRepository;
import com.minipay.audit.ActorSource;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import com.minipay.permission.PermissionCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipay.common.exception.BadRequestException;
import com.minipay.common.exception.ConflictException;
import com.minipay.role.dto.RoleOptionsResponse;
import com.minipay.common.exception.RoleNotFoundException;
import com.minipay.role.dto.CreateRoleRequest;
import com.minipay.role.dto.RoleResponse;
import com.minipay.role.dto.UpdateRolePermissionsRequest;
import com.minipay.session.SessionRevocationService;
import com.minipay.user.SystemRole;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.user.UserState;

@Service
public class RoleService {
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AccessAuditRepository accessAuditRepository;
    private final SessionRevocationService sessionRevocationService;

    public RoleService(UserRepository userRepository, PermissionRepository permissionRepository,
            RoleRepository roleRepository, AccessAuditRepository accessAuditRepository,
            SessionRevocationService sessionRevocationService) {
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.accessAuditRepository = accessAuditRepository;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Transactional
    public Role createRole(Long userId, CreateRoleRequest request) {
        requireActiveSuperAdmin(userId);

        String name = validateRoleName(request.name());

        Role role = new Role(name, request.rank());
        role.getPermissions().addAll(resolvePermissions(request.permissionCodes()));
        Role saved = roleRepository.saveAndFlush(role);
        var after = permissionSnapshot(permissionCodes(saved.getPermissions()))
                .put("name", saved.getName())
                .put("rank", saved.getRank());
        accessAuditRepository.save(new AccessAudit(
                Instant.now(), userId, ActorSource.USER, "ROLE_CREATE",
                null, saved.getId(), null, after));
        return saved;
    }

    private Set<Permission> resolvePermissions(List<String> requestedCodes) {
        Set<Permission> permissions = new LinkedHashSet<>();
        for (String code : new LinkedHashSet<>(requestedCodes)) {
            PermissionCode permissionCode;
            try {
                permissionCode = PermissionCode.valueOf(code);
            } catch (IllegalArgumentException exception) {
                throw new BadRequestException("Unknown permission code: " + code);
            }

            if (!permissionCode.isAssignableToServiceRole()) {
                throw new BadRequestException("Permission cannot be assigned to a service role: " + code);
            }

            Permission permission = permissionRepository.findByCode(permissionCode.name())
                    .orElseThrow(() -> new IllegalStateException(
                            "Permission missing from database catalog: " + permissionCode.name()));
            permissions.add(permission);
        }
        return permissions;
    }

    @Transactional
    public RoleResponse updateRolePermissions(Long actorId, Long roleId, UpdateRolePermissionsRequest request) {
        requireActiveSuperAdmin(actorId);
        Role role = roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        Set<Permission> replacement = resolvePermissions(request.permissionCodes());
        List<String> beforeCodes = permissionCodes(role.getPermissions());
        List<String> afterCodes = permissionCodes(replacement);
        if (beforeCodes.equals(afterCodes)) {
            return toResponse(role);
        }

        List<User> employees = userRepository.findByMainRoleIdForUpdate(roleId);
        role.getPermissions().clear();
        role.getPermissions().addAll(replacement);
        Instant now = Instant.now();
        var after = permissionSnapshot(afterCodes);
        var removedExtras = after.putArray("removedExtraPermissions");
        Set<String> newCodes = Set.copyOf(afterCodes);
        for (User employee : employees) {
            var extras = employee.getExtraPermissions();
            List<String> removed = permissionCodes(extras).stream().filter(newCodes::contains).toList();
            if (!removed.isEmpty()) {
                extras.removeIf(permission -> newCodes.contains(permission.getCode()));
                var removedCodes = removedExtras.addObject().put("userId", employee.getId())
                        .putArray("permissionCodes");
                removed.forEach(removedCodes::add);
            }
            sessionRevocationService.revokeAllForUser(employee.getId(), "ROLE_PERMISSIONS_UPDATE");
        }
        accessAuditRepository.save(new AccessAudit(now, actorId, ActorSource.USER, "ROLE_PERMISSIONS_UPDATE",
                null, roleId, permissionSnapshot(beforeCodes), after));
        return toResponse(role);
    }

    private static List<String> permissionCodes(Set<Permission> permissions) {
        return permissions.stream().map(Permission::getCode).sorted().toList();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode permissionSnapshot(List<String> codes) {
        var snapshot = JsonNodeFactory.instance.objectNode();
        var array = snapshot.putArray("permissionCodes");
        codes.forEach(array::add);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles(Long userId) {
        requireActiveSuperAdmin(userId);
        return roleRepository.findAll(Sort.by("id")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RoleResponse renameRole(Long actorId, Long roleId, String newName) {
        requireActiveSuperAdmin(actorId);

        Role role = roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        String oldName = role.getName();
        String name = validateRoleName(newName);
        if (oldName.equals(name)) {
            return toResponse(role);
        }

        role.setName(name);
        roleRepository.flush();
        var json = JsonNodeFactory.instance;
        accessAuditRepository.save(new AccessAudit(
                Instant.now(), actorId, ActorSource.USER, "ROLE_RENAME",
                null, roleId, json.objectNode().put("name", oldName),
                json.objectNode().put("name", name)));
        return toResponse(role);
    }

    @Transactional
    public RoleResponse updateRank(Long actorId, Long roleId, int rank) {
        requireActiveSuperAdmin(actorId);
        if (rank <= 0) throw new BadRequestException("Rank must be positive");
        Role role = lockRole(roleId);
        int before = role.getRank();
        if (before != rank) {
            role.setRank(rank);
            recordAccessChange(actorId, role, "ROLE_RANK_UPDATE",
                    JsonNodeFactory.instance.objectNode().put("rank", before),
                    JsonNodeFactory.instance.objectNode().put("rank", rank));
        }
        return toResponse(role);
    }

    @Transactional
    public RoleOptionsResponse updateAssignableRoles(Long actorId, Long roleId, List<Long> ids) {
        requireActiveSuperAdmin(actorId);
        var roles = roleRepository.findAllForUpdate();
        Role role = roles.stream().filter(r -> r.getId().equals(roleId)).findFirst()
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        var requested = new LinkedHashSet<>(ids);
        var replacement = roles.stream().filter(r -> requested.contains(r.getId())).toList();
        if (replacement.size() != requested.size() || requested.contains(roleId)) {
            throw new BadRequestException("Assignable roles must exist and cannot include the role itself");
        }
        var before = roleIds(role.getAssignableRoles());
        var after = roleIds(replacement);
        if (!before.equals(after)) {
            role.getAssignableRoles().clear();
            role.getAssignableRoles().addAll(replacement);
            recordAccessChange(actorId, role, "ROLE_ASSIGNABLE_ROLES_UPDATE",
                    roleIdSnapshot(before), roleIdSnapshot(after));
        }
        return toOptions(role);
    }

    @Transactional
    public RoleOptionsResponse updateGrantablePermissions(Long actorId, Long roleId, List<String> codes) {
        requireActiveSuperAdmin(actorId);
        Role role = lockRole(roleId);
        var replacement = resolvePermissions(codes);
        var before = permissionCodes(role.getGrantablePermissions());
        var after = permissionCodes(replacement);
        if (!before.equals(after)) {
            role.getGrantablePermissions().clear();
            role.getGrantablePermissions().addAll(replacement);
            recordAccessChange(actorId, role, "ROLE_GRANTABLE_PERMISSIONS_UPDATE",
                    permissionSnapshot(before), permissionSnapshot(after));
        }
        return toOptions(role);
    }

    @Transactional
    public void deleteRole(Long actorId, Long roleId) {
        requireActiveSuperAdmin(actorId);
        var roles = roleRepository.findAllForUpdate();
        Role target = roles.stream().filter(r -> r.getId().equals(roleId)).findFirst()
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        if (!userRepository.findByMainRoleIdForUpdate(roleId).isEmpty()) {
            throw new ConflictException("Role is still assigned");
        }
        for (Role role : roles) {
            if (role.getId().equals(roleId)) continue;
            var before = roleIds(role.getAssignableRoles());
            if (role.getAssignableRoles().removeIf(r -> r.getId().equals(roleId))) {
                recordAccessChange(actorId, role, "ROLE_ASSIGNABLE_ROLES_UPDATE",
                        roleIdSnapshot(before), roleIdSnapshot(roleIds(role.getAssignableRoles())));
            }
        }
        var before = permissionSnapshot(permissionCodes(target.getPermissions()))
                .put("name", target.getName()).put("rank", target.getRank());
        before.set("assignableRoleIds", roleIdSnapshot(roleIds(target.getAssignableRoles()))
                .get("assignableRoleIds"));
        before.set("grantablePermissionCodes", permissionSnapshot(permissionCodes(target.getGrantablePermissions()))
                .get("permissionCodes"));
        roleRepository.flush();
        roleRepository.delete(target);
        audit(actorId, target, "ROLE_DELETE", before, null);
    }

    @Transactional(readOnly = true)
    public RoleOptionsResponse roleOptions(Long actorId) {
        User actor = userRepository.findById(actorId)
                .filter(u -> u.getState() == UserState.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
        if (actor.getSystemRole() == SystemRole.SUPER_ADMIN) {
            var catalog = permissionCodes(new LinkedHashSet<>(permissionRepository.findAll()));
            return new RoleOptionsResponse(roleRepository.findAll(Sort.by("id")).stream()
                    .map(RoleService::summary).toList(),
                    catalog.stream()
                            .filter(code -> PermissionCode.valueOf(code).isAssignableToServiceRole())
                            .toList(), catalog);
        }
        if (actor.getMainRole() == null) throw new AccessDeniedException("Access denied");
        return toOptions(actor.getMainRole());
    }

    private Role lockRole(Long roleId) {
        return roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
    }

    private static RoleOptionsResponse toOptions(Role role) {
        return new RoleOptionsResponse(role.getAssignableRoles().stream()
                .sorted(java.util.Comparator.comparing(Role::getId))
                .map(RoleService::summary).toList(), permissionCodes(role.getGrantablePermissions()), List.of());
    }

    private static RoleOptionsResponse.RoleSummary summary(Role role) {
        return new RoleOptionsResponse.RoleSummary(role.getId(), role.getName(), role.getRank());
    }

    private static List<Long> roleIds(java.util.Collection<Role> roles) {
        return roles.stream().map(Role::getId).sorted().toList();
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode roleIdSnapshot(List<Long> ids) {
        var snapshot = JsonNodeFactory.instance.objectNode();
        var values = snapshot.putArray("assignableRoleIds");
        ids.forEach(values::add);
        return snapshot;
    }

    private void recordAccessChange(Long actorId, Role role, String action,
            com.fasterxml.jackson.databind.JsonNode before, com.fasterxml.jackson.databind.JsonNode after) {
        for (User employee : userRepository.findByMainRoleIdForUpdate(role.getId())) {
            sessionRevocationService.revokeAllForUser(employee.getId(), action);
        }
        audit(actorId, role, action, before, after);
    }

    private void audit(Long actorId, Role role, String action,
            com.fasterxml.jackson.databind.JsonNode before, com.fasterxml.jackson.databind.JsonNode after) {
        accessAuditRepository.save(new AccessAudit(Instant.now(), actorId, ActorSource.USER,
                action, null, role.getId(), before, after));
    }

    private RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId(), role.getName(), role.getRank(),
                role.getPermissions().stream().map(Permission::getCode).sorted().toList());
    }

    private void requireActiveSuperAdmin(Long userId) {
        userRepository.findById(userId)
                .filter(user -> user.getSystemRole() == SystemRole.SUPER_ADMIN
                        && user.getState() == UserState.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Access denied"));
    }

    private String validateRoleName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new BadRequestException("Role name must not be blank");
        }

        String name = rawName.strip();
        if (name.length() > 50) {
            throw new BadRequestException("Role name must not exceed 50 characters");
        }
        if ("USER".equalsIgnoreCase(name) || "SUPER_ADMIN".equalsIgnoreCase(name)) {
            throw new BadRequestException("Role name is reserved");
        }

        return name;
    }
}
