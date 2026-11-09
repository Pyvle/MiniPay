package com.minipay.user;

import com.minipay.support.TestUsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.minipay.common.exception.EmailAlreadyExistsException;
import com.minipay.common.exception.UserNotFoundException;
import com.minipay.permission.Permission;
import com.minipay.permission.PermissionCode;
import com.minipay.permission.PermissionService;
import com.minipay.role.Role;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @Test
    void foreignProfileShouldBeReturnedWithReadAnyPermission() {
        User target = TestUsers.create("Target", "target@example.com");
        when(permissionService.hasPermission(1L, PermissionCode.PROFILE_READ_ANY)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        assertSame(target, userService.getProfileForViewer(1L, 2L));
    }

    @Test
    void foreignProfileShouldNotBeLookedUpWithoutReadAnyPermission() {
        assertThrows(UserNotFoundException.class, () -> userService.getProfileForViewer(1L, 2L));
        verify(userRepository, never()).findById(2L);
    }

    @InjectMocks
    private UserService userService;

    private String name;
    private String email;

    @BeforeEach
    void setUp() {
        name = "Ivan";
        email = "Ivan@email.com";
    }

    // createUser
    @Test
    void createUserShouldCreateUser() {

        when(userRepository.existsByEmail(email))
                .thenReturn(false);
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(name, email);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();

        assertSame(result, savedUser);
        assertEquals(name, savedUser.getName());
        assertEquals(email, savedUser.getEmail());
        verify(userRepository).existsByEmail(email);
    }

    @Test
    void createUserShouldThrowWhenUserExists() {
        when(userRepository.existsByEmail(email))
                .thenReturn(true);

        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.createUser(name, email));

        assertEquals("Email already exists: Ivan@email.com",
                exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserByIdShouldReturnUser() {
        User user = TestUsers.create(name, email);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        User result = userService.getUserById(1L);

        assertSame(user, result);
    }

    @Test
    void getUserByIdShouldThrowWhenUserNotFound() {

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(
                UserNotFoundException.class,
                () -> userService.getUserById(999L));

        assertEquals("User not found: 999", exception.getMessage());

    }

    @Test
    void getAllUsersShouldReturnAllUsers() {
        when(permissionService.hasPermission(1L, PermissionCode.USER_LIST)).thenReturn(true);
        User ivan = TestUsers.create("Ivan", "ivan@email.com");
        User anna = TestUsers.create("Anna", "anna@email.com");

        List<User> users = List.of(ivan, anna);

        when(userRepository.findAll())
                .thenReturn(users);

        List<User> result = userService.getAllUsers(1L);

        assertEquals(users, result);
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsersShouldReturnEmptyListWhenUsersDoNotExist() {
        when(permissionService.hasPermission(1L, PermissionCode.USER_LIST)).thenReturn(true);
        when(userRepository.findAll())
                .thenReturn(List.of());

        List<User> result = userService.getAllUsers(1L);

        assertTrue(result.isEmpty());
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsersShouldRejectWithoutPermissionBeforeReadingUsers() {
        assertThrows(AccessDeniedException.class, () -> userService.getAllUsers(1L));
        verify(userRepository, never()).findAll();
    }

    @ParameterizedTest
    @CsvSource({
            "NONE, false, false",
            "PROFILE_READ_ANY, true, false",
            "USER_LIST, false, true",
            "SUPER_ADMIN, true, true"
    })
    void profileAndListShouldRequireIndependentPermissions(
            String assignment, boolean canReadProfile, boolean canList) {
        long actorId = 1L;
        long targetId = 2L;
        User actor = TestUsers.create("Actor", "actor@example.com");
        User target = TestUsers.create("Target", "target@example.com");
        switch (assignment) {
            case "PROFILE_READ_ANY" -> {
                Role role = new Role("Support", 1);
                role.getPermissions().add(new Permission(PermissionCode.PROFILE_READ_ANY.name()));
                actor.setMainRole(role);
            }
            case "USER_LIST" -> {
                actor.setMainRole(new Role("Support", 1));
                actor.getExtraPermissions().add(new Permission(PermissionCode.USER_LIST.name()));
            }
            case "SUPER_ADMIN" -> actor.setSystemRole(SystemRole.SUPER_ADMIN);
            case "NONE" -> { }
            default -> throw new IllegalArgumentException(assignment);
        }

        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        UserService service = new UserService(userRepository, new PermissionService(userRepository));

        if (canReadProfile) {
            when(userRepository.findById(targetId)).thenReturn(Optional.of(target));
            assertSame(target, service.getProfileForViewer(actorId, targetId));
        } else {
            assertThrows(UserNotFoundException.class, () -> service.getProfileForViewer(actorId, targetId));
            verify(userRepository, never()).findById(targetId);
        }

        if (canList) {
            when(userRepository.findAll()).thenReturn(List.of(actor, target));
            assertEquals(List.of(actor, target), service.getAllUsers(actorId));
        } else {
            assertThrows(AccessDeniedException.class, () -> service.getAllUsers(actorId));
            verify(userRepository, never()).findAll();
        }
    }
}
