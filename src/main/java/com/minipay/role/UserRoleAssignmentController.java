package com.minipay.role;

import com.minipay.permission.PermissionCode;
import io.swagger.v3.oas.annotations.Operation;
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

    @Operation(summary = "Назначить SUPER_ADMIN", description = "Только активный SUPER_ADMIN.")
    @PutMapping("/super-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void promote(@PathVariable("userId") Long userId, Authentication authentication) {
        superAdmins.promoteToSuperAdmin(actorId(authentication), userId);
    }

    @Operation(summary = "Снять статус SUPER_ADMIN", description = "Защита последнего активного SUPER_ADMIN действует и при одновременных запросах.")
    @DeleteMapping("/super-admin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSuperAdmin(@PathVariable("userId") Long userId, Authentication authentication) {
        superAdmins.removeSuperAdmin(actorId(authentication), userId);
    }

    @Operation(summary = "Заблокировать пользователя и отозвать его сессии",
            description = "Для SUPER_ADMIN или руководителя с USER_BLOCK, с учётом ранга и зоны управления.")
    @PostMapping("/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@PathVariable("userId") Long userId, Authentication authentication) {
        blocking.block(actorId(authentication), userId);
    }

    @Operation(summary = "Разблокировать пользователя",
            description = "Для SUPER_ADMIN или руководителя с USER_UNBLOCK, с учётом ранга и зоны управления. Старые токены остаются недействительными.")
    @PostMapping("/unblock")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@PathVariable("userId") Long userId,
            Authentication authentication) {
        blocking.unblock(actorId(authentication), userId);
    }

    @Operation(summary = "Назначить или заменить основную служебную роль",
            description = "Для SUPER_ADMIN или руководителя с MAIN_ROLE_ASSIGN в пределах его полномочий.")
    @PutMapping("/main-role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assign(@PathVariable Long userId, @Valid @RequestBody MainRoleRequest request,
            Authentication authentication) {
        assignments.assignMainRole(actorId(authentication), userId, request.roleId());
    }

    @Operation(summary = "Снять основную служебную роль и дополнительные права",
            description = "Для SUPER_ADMIN или руководителя с MAIN_ROLE_REMOVE в пределах его полномочий.")
    @DeleteMapping("/main-role")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long userId, Authentication authentication) {
        assignments.removeMainRole(actorId(authentication), userId);
    }

    @Operation(summary = "Выдать дополнительное право",
            description = "Для SUPER_ADMIN или руководителя с EXTRA_PERMISSION_GRANT в пределах его полномочий.")
    @PutMapping("/extra-permissions/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grant(@PathVariable Long userId, @PathVariable PermissionCode code,
            Authentication authentication) {
        assignments.grantExtraPermission(actorId(authentication), userId, code);
    }

    @Operation(summary = "Снять дополнительное право",
            description = "Для SUPER_ADMIN или руководителя с EXTRA_PERMISSION_REVOKE в пределах его полномочий.")
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
