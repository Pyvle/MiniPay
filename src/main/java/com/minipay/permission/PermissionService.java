package com.minipay.permission;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipay.role.Role;
import com.minipay.user.SystemRole;
import com.minipay.user.User;
import com.minipay.user.UserRepository;

@Service
public class PermissionService {
    private final UserRepository userRepository;

    public PermissionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, PermissionCode permission) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }

        if (user.getSystemRole() == SystemRole.SUPER_ADMIN)
            return true;

        Role mainRole = user.getMainRole();
        if (mainRole == null) {
            return false;
        }

        String code = permission.name();
        return containsPermission(mainRole.getPermissions(), code)
                || containsPermission(user.getExtraPermissions(), code);
    }

    private boolean containsPermission(Set<Permission> permissions, String code) {
        for (Permission permission : permissions) {
            if (code.equals(permission.getCode())) {
                return true;
            }
        }
        return false;
    }
}
