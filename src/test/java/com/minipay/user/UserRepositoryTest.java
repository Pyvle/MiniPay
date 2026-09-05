package com.minipay.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

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

    @Test
    void saveShouldPersistUser() {
        User user = new User("Ivan", "ivan@example.com");

        User savedUser = userRepository.saveAndFlush(user);

        assertNotNull(savedUser.getId());
        assertEquals("Ivan", savedUser.getName());
        assertEquals("ivan@example.com", savedUser.getEmail());
        assertNotNull(savedUser.getCreatedAt());
    }

    @Test
    void findByEmailShouldReturnUser() {
        User user = new User("Ivan", "ivan@example.com");
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
        User user = new User("Ivan", "ivan@example.com");
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
        User user = new User("Ivan", "ivan@example.com");
        User user2 = new User("Anna", "ivan@example.com");

        userRepository.saveAndFlush(user);
        
        assertThrows(DataIntegrityViolationException.class,
            () -> userRepository.saveAndFlush(user2));
    }
}
