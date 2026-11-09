package com.minipay.user;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.minipay.common.exception.EmailAlreadyExistsException;
import com.minipay.common.exception.UserNotFoundException;
import com.minipay.permission.PermissionCode;
import com.minipay.permission.PermissionService;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public UserService(UserRepository userRepository, PermissionService permissionService) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    public User createUser(String name, String email) {
        if(userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = new User(name, email);
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public User getProfileForViewer(Long actorId, Long profileId) {
        if (!actorId.equals(profileId)
                && !permissionService.hasPermission(actorId, PermissionCode.PROFILE_READ_ANY)) {
            throw new UserNotFoundException(profileId);
        }
        return getUserById(profileId);
    }

    public List<User> getAllUsers(Long actorId) {
        if (!permissionService.hasPermission(actorId, PermissionCode.USER_LIST)) {
            throw new AccessDeniedException("Access denied");
        }
        return userRepository.findAll();
    }
    
}
