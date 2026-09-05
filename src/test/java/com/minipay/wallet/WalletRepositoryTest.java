package com.minipay.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import com.minipay.support.PostgresTestConfiguration;
import com.minipay.user.User;
import com.minipay.user.UserRepository;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class WalletRepositoryTest {
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        User user = new User("Ivan", "ivan@example.com");
        savedUser = userRepository.saveAndFlush(user);
    }

    @Test
    void saveShouldPersistWallet() {
        Wallet wallet = new Wallet(savedUser);

        Wallet savedWallet = walletRepository.saveAndFlush(wallet);

        assertNotNull(savedWallet.getId());
        assertEquals(wallet.getUser(), savedWallet.getUser());
        assertThat(savedWallet.getBalance())
            .isEqualByComparingTo(BigDecimal.ZERO);
        assertNotNull(savedWallet.getCreatedAt());
    }

    @Test
    void findByUserIdShouldReturnWallet() {
        Wallet wallet = new Wallet(savedUser);
        walletRepository.saveAndFlush(wallet);

        Optional<Wallet> result = walletRepository.findByUserId(savedUser.getId());

        assertTrue(result.isPresent());
        assertNotNull(result.get().getId());
        assertEquals(wallet.getUser(), result.get().getUser());
        assertThat(result.get().getBalance())
            .isEqualByComparingTo(BigDecimal.ZERO);
        assertNotNull(result.get().getCreatedAt());
    }

    @Test
    void findByUserIdShouldReturnEmptyWhenWalletDoesNotExist() {
        
        Optional<Wallet> result = walletRepository.findByUserId(savedUser.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    void existsByUserIdShouldReturnTrue() {
        Wallet wallet = new Wallet(savedUser);
        walletRepository.saveAndFlush(wallet);

        Boolean result = walletRepository.existsByUserId(savedUser.getId());

        assertTrue(result);
    }

    @Test
    void existsByUserIdShouldReturnFalse() {
        Boolean result = walletRepository.existsByUserId(savedUser.getId());

        assertFalse(result);
    }

    @Test
    void saveShouldThrowWhenUserAlreadyHasWallet() {
        Wallet wallet = new Wallet(savedUser);
        Wallet wallet2 = new Wallet(savedUser);
        walletRepository.saveAndFlush(wallet);

        assertThrows(DataIntegrityViolationException.class,
            () -> walletRepository.saveAndFlush(wallet2)
        );
    }
}
