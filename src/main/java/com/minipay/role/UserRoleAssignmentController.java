package com.minipay.role;

import com.minipay.permission.PermissionCode;
import com.minipay.user.SuperAdminService;
import com.minipay.user.UserBlockingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users/{userId}")
public class UserRoleAssignmentController {
    private final UserRoleAssignmentService assignments;
    private final UserBlockingService blocking;
    private final SuperAdminService superAdmins;

    public UserRoleAssignmentController(UserRoleAssignmentService assignments, UserBlockingService blocking,
            SuperAdminService superAdmins) {
        this.assignments = assignments;
        this.blocking = blocking;
        this.superAdmins = superAdmins;
    }

    @PutMapping("/super-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void promote(@PathVariable("userId") Long userId, Authentication authentication) {
        superAdmins.promoteToSuperAdmin(actorId(authentication), userId);
    }

    @DeleteMapping("/super-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSuperAdmin(@PathVariable("userId") Long userId, Authentication authentication) {
        superAdmins.removeSuperAdmin(actorId(authentication), userId);
    }

    @PostMapping("/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable("userId") Long userId, Authentication authentication) {
        blocking.block(actorId(authentication), userId);
    }

    @PostMapping("/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable("userId") Long userId,
            Authentication authentication) {
        blocking.unblock(actorId(authentication), userId);
    }

    @PutMapping("/main-role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable Long userId, @Valid @RequestBody MainRoleRequest request,
            Authentication authentication) {
        assignments.assignMainRole(actorId(authentication), userId, request.roleId());
    }

    @DeleteMapping("/main-role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long userId, Authentication authentication) {
        assignments.removeMainRole(actorId(authentication), userId);
    }

    @PutMapping("/extra-permissions/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(@PathVariable Long userId, @PathVariable PermissionCode code,
            Authentication authentication) {
        assignments.grantExtraPermission(actorId(authentication), userId, code);
    }

    @DeleteMapping("/extra-permissions/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long userId, @PathVariable PermissionCode code,
            Authentication authentication) {
        assignments.revokeExtraPermission(actorId(authentication), userId, code);
    }

    private static Long actorId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }

    public record MainRoleRequest(@NotNull @Positive Long roleId) {
    }
}
