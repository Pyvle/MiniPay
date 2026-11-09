package com.minipay.user;

import com.minipay.support.TestUsers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.minipay.role.Role;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.minipay.support.PostgresTestConfiguration;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class UserRepositoryTest {
    
    @Autowired
    private UserRepository userRepository;

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void defaultAccessFieldsShouldSurviveReload() {
        User saved = userRepository.saveAndFlush(TestUsers.create("Alice", "defaults@example.com"));
        entityManager.clear();
        User loaded = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals(TestUsers.PASSWORD_HASH, loaded.getPasswordHash());
        assertEquals(SystemRole.USER, loaded.getSystemRole());
        assertEquals(UserState.ACTIVE, loaded.getState());
        org.junit.jupiter.api.Assertions.assertNull(loaded.getMainRole());
    }

    @Test
    void mainRoleAndBlockedStateShouldSurviveReload() {
        Role role = new Role("Support", 1);
        entityManager.persist(role);
        User user = TestUsers.create("Alice", "blocked@example.com");
        user.setMainRole(role);
        user.setState(UserState.BLOCKED);
        userRepository.saveAndFlush(user);
        entityManager.clear();
        User loaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(role.getId(), loaded.getMainRole().getId());
        assertEquals("Support", loaded.getMainRole().getName());
        assertEquals(UserState.BLOCKED, loaded.getState());
    }

    @Test
    void superAdminWithoutMainRoleShouldPersist() {
        User user = TestUsers.create("Admin", "admin@example.com");
        user.setSystemRole(SystemRole.SUPER_ADMIN);
        userRepository.saveAndFlush(user);
        entityManager.clear();
        assertEquals(SystemRole.SUPER_ADMIN,
                userRepository.findById(user.getId()).orElseThrow().getSystemRole());
    }

    @Test
    void superAdminWithMainRoleShouldBeRejected() {
        Role role = new Role("Support", 1);
        entityManager.persist(role);
        entityManager.flush();
        User user = TestUsers.create("Admin", "admin@example.com");
        user.setSystemRole(SystemRole.SUPER_ADMIN);
        user.setMainRole(role);
        assertThrows(DataIntegrityViolationException.class, () -> userRepository.saveAndFlush(user));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password_hash", "system_role", "state"})
    void requiredAccessColumnShouldRejectNull(String column) {
        User user = userRepository.saveAndFlush(TestUsers.create("Alice", "required@example.com"));
        // Column names come only from the fixed test cases above.
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE users SET " + column + " = NULL WHERE id = ?", user.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"system_role", "state"})
    void unknownEnumValueShouldBeRejected(String column) {
        User user = userRepository.saveAndFlush(TestUsers.create("Alice", "enum@example.com"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE users SET " + column + " = ? WHERE id = ?", "UNKNOWN", user.getId()));
    }

    @Test
    void missingMainRoleShouldBeRejected() {
        User user = userRepository.saveAndFlush(TestUsers.create("Alice", "missing-role@example.com"));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE users SET main_role_id = ? WHERE id = ?", -1L, user.getId()));
    }

    @Test
    void saveShouldPersistUser() {
        User user = TestUsers.create("Ivan", "ivan@example.com");

        User savedUser = userRepository.saveAndFlush(user);

        assertNotNull(savedUser.getId());
        assertEquals("Ivan", savedUser.getName());
        assertEquals("ivan@example.com", savedUser.getEmail());
        assertNotNull(savedUser.getCreatedAt());
    }

    @Test
    void findByEmailShouldReturnUser() {
        User user = TestUsers.create("Ivan", "ivan@example.com");
        userRepository.saveAndFlush(user);

        Optional<User> foundUser = userRepository.findByEmail("ivan@example.com");

        assertTrue(foundUser.isPresent());
        assertEquals(user.getId(), foundUser.get().getId());
        assertEquals(user.getName(), foundUser.get().getName());
        assertEquals(user.getCreatedAt(), foundUser.get().getCreatedAt());
    }

    @Test
    void findByEmailShouldReturnEmptyWhenUserDoesNotExist() {
        Optional<User> result = userRepository.findByEmail("missing@example.com");

        assertTrue(result.isEmpty());
    }

    @Test
    void existsByEmailShouldReturnTrue() {
        User user = TestUsers.create("Ivan", "ivan@example.com");
        userRepository.saveAndFlush(user);

        Boolean result = userRepository.existsByEmail("ivan@example.com");

        assertTrue(result);
    }

    @Test
    void existsByEmailShouldReturnFalse() {
        Boolean result = userRepository.existsByEmail("ivan@example.com");

        assertFalse(result);
    }

    @Test
    void saveShouldThrowWhenEmailAlreadyExists() {
        User user = TestUsers.create("Ivan", "ivan@example.com");
        User user2 = TestUsers.create("Anna", "ivan@example.com");

        userRepository.saveAndFlush(user);
        
        assertThrows(DataIntegrityViolationException.class,
            () -> userRepository.saveAndFlush(user2));
    }
}
