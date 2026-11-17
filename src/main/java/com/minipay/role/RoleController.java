package com.minipay.role;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.minipay.permission.Permission;
import com.minipay.role.dto.CreateRoleRequest;
import com.minipay.role.dto.RenameRoleRequest;
import com.minipay.role.dto.RoleResponse;
import com.minipay.role.dto.RoleOptionsResponse;
import com.minipay.role.dto.UpdateRolePermissionsRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(@Valid @RequestBody CreateRoleRequest createRoleRequest,
            Authentication authentication) {

        Long userId = Long.valueOf(authentication.getName());
        Role role = roleService.createRole(userId, createRoleRequest);

        List<String> permissionCodes = role.getPermissions().stream()
                .map(Permission::getCode)
                .sorted()
                .toList();

        return new RoleResponse(
                role.getId(), role.getName(), role.getRank(), permissionCodes);
    }

    @GetMapping
    public List<RoleResponse> listRoles(Authentication authentication) {
        Long userId = Long.valueOf(authentication.getName());
        return roleService.listRoles(userId);
    }

    @PatchMapping("/{id}/name")
    public RoleResponse renameRole(@PathVariable("id") Long roleId,
            @Valid @RequestBody RenameRoleRequest request, Authentication authentication) {
        Long actorId = Long.valueOf(authentication.getName());
        return roleService.renameRole(actorId, roleId, request.name());
    }

    @PatchMapping("/{id}/rank")
    public RoleResponse updateRank(@PathVariable("id") Long roleId,
            @Valid @RequestBody UpdateRankRequest request, Authentication authentication) {
        return roleService.updateRank(Long.valueOf(authentication.getName()), roleId, request.rank());
    }

    @PutMapping("/{id}/assignable-roles")
    public RoleOptionsResponse updateAssignableRoles(@PathVariable("id") Long roleId,
            @Valid @RequestBody UpdateAssignableRolesRequest request, Authentication authentication) {
        return roleService.updateAssignableRoles(
                Long.valueOf(authentication.getName()), roleId, request.roleIds());
    }

    @PutMapping("/{id}/grantable-permissions")
    public RoleOptionsResponse updateGrantablePermissions(@PathVariable("id") Long roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request, Authentication authentication) {
        return roleService.updateGrantablePermissions(
                Long.valueOf(authentication.getName()), roleId, request.permissionCodes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable("id") Long roleId, Authentication authentication) {
        roleService.deleteRole(Long.valueOf(authentication.getName()), roleId);
    }

    @GetMapping("/options")
    public RoleOptionsResponse roleOptions(Authentication authentication) {
        return roleService.roleOptions(Long.valueOf(authentication.getName()));
    }

    public record UpdateRankRequest(@NotNull @Positive Integer rank) {}

    public record UpdateAssignableRolesRequest(@NotNull List<@NotNull @Positive Long> roleIds) {}

    @PutMapping("/{id}/permissions")
    public RoleResponse updateRolePermissions(
            @PathVariable("id") Long roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request,
            Authentication authentication) {

        Long actorId = Long.valueOf(authentication.getName());
        return roleService.updateRolePermissions(actorId, roleId, request);
    }
}
