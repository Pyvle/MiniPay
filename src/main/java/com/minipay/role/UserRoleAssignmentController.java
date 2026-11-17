package com.minipay.role;

import com.minipay.permission.PermissionCode;
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

    public UserRoleAssignmentController(UserRoleAssignmentService assignments) {
        this.assignments = assignments;
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

    public record MainRoleRequest(@NotNull @Positive Long roleId) {}
}
