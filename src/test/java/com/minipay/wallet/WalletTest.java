package com.minipay.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.minipay.common.exception.InsufficientBalanceException;

class WalletTest {

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = new Wallet();
    }

    @Test
    void depositShouldIncreaseBalance() {
        // Act
        wallet.deposit(new BigDecimal("100.00"));

        // Assert
        assertEquals(new BigDecimal("100.00"), wallet.getBalance());
    }

    @Test
    void withdrawShouldDecreaseBalance() {

        wallet.deposit(new BigDecimal("200.00"));
        wallet.withdraw(new BigDecimal("100.00"));

        assertEquals(new BigDecimal("100.00"), wallet.getBalance());
    }

    @Test
    void withdrawShouldThrowWhenBalanceIsInsufficient() {

        wallet.deposit(new BigDecimal("100.00"));

        assertThrows(InsufficientBalanceException.class,
                () -> wallet.withdraw(new BigDecimal("200.00")));
        assertEquals(new BigDecimal("100.00"), wallet.getBalance());
    }

}
