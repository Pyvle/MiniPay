package com.minipay.user;

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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

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

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.createUser(name, email));

        assertEquals("User with this email already exists", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUserByIdShouldReturnUser() {
        User user = new User(name, email);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        User result = userService.getUserById(1L);

        assertSame(user, result);
    }

    @Test
    void getUserByIdShouldThrowWhenUserNotFound() {

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.getUserById(999L));

        assertEquals("User not found", exception.getMessage());

    }

    @Test
    void getAllUsersShouldReturnAllUsers() {
        User ivan = new User("Ivan", "ivan@email.com");
        User anna = new User("Anna", "anna@email.com");

        List<User> users = List.of(ivan, anna);

        when(userRepository.findAll())
                .thenReturn(users);

        List<User> result = userService.getAllUsers();

        assertEquals(users, result);
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsersShouldReturnEmptyListWhenUsersDoNotExist() {
        when(userRepository.findAll())
                .thenReturn(List.of());

        List<User> result = userService.getAllUsers();

        assertTrue(result.isEmpty());
        verify(userRepository).findAll();
    }
}
