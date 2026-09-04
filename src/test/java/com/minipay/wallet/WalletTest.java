package com.minipay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.minipay.common.exception.BalanceLimitExceededException;
import com.minipay.common.exception.InsufficientBalanceException;
import com.minipay.common.exception.InvalidAmountException;

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
    void depositShouldThrowAndKeepBalanceUnchangedWhenAmountIsNegative() {

        wallet.deposit(new BigDecimal("100.00"));

        assertThrows(InvalidAmountException.class,
                () -> wallet.deposit(new BigDecimal("-100.00")));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void depositShouldThrowAndKeepBalanceUnchangedWhenAmountIsNull() {

        wallet.deposit(new BigDecimal("100.00"));

        assertThrows(InvalidAmountException.class,
                () -> wallet.deposit(null));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void depositShouldThrowAndKeepBalanceUnchangedWhenAmountIsZero() {

        wallet.deposit(new BigDecimal("100.00"));

        assertThrows(InvalidAmountException.class,
                () -> wallet.deposit(BigDecimal.ZERO));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void depositShouldThrowAndKeepBalanceUnchangedWhenAmountHasFractionOfCent() {
        wallet.deposit(new BigDecimal("100.00"));

        InvalidAmountException exception = assertThrows(InvalidAmountException.class,
                () -> wallet.deposit(new BigDecimal("10.001")));

        assertEquals("Amount must not have fractions of a cent", exception.getMessage());
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void depositShouldIncreaseBalanceWhenAmountHasTrailingZeros() {
        wallet.deposit(new BigDecimal("100.00"));

        wallet.deposit(new BigDecimal("10.000"));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    void depositShouldSucceedWhenResultingBalanceEqualsLimit() {
        wallet.deposit(new BigDecimal("99999999999999999.98"));

        wallet.deposit(new BigDecimal("0.01"));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("99999999999999999.99"));
    }

    @Test
    void depositShouldThrowAndKeepBalanceUnchangedWhenResultingBalanceExceedsLimit() {
        wallet.deposit(new BigDecimal("99999999999999999.99"));

        assertThrows(BalanceLimitExceededException.class,
                () -> wallet.deposit(new BigDecimal("0.01")));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("99999999999999999.99"));
    }

    @Test
    void depositShouldThrowAndKeepBalanceUnchangedWhenAmountExceedsLimit() {
        wallet.deposit(new BigDecimal("100.00"));

        InvalidAmountException exception = assertThrows(
                InvalidAmountException.class,
                () -> wallet.deposit(new BigDecimal("100000000000000000.00")));
            
        assertEquals("Maximum amount exceeded", exception.getMessage());
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
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

    @Test
    void withdrawShouldThrowAndKeepBalanceUnchangedWhenAmountIsNegative() {

        wallet.deposit(new BigDecimal("100.00"));

        assertThrows(InvalidAmountException.class,
                () -> wallet.withdraw(new BigDecimal("-100.00")));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void withdrawShouldThrowAndKeepBalanceUnchangedWhenAmountIsNull() {

        wallet.deposit(new BigDecimal("100.00"));

        assertThrows(InvalidAmountException.class,
                () -> wallet.withdraw(null));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void withdrawShouldThrowAndKeepBalanceUnchangedWhenAmountIsZero() {

        wallet.deposit(new BigDecimal("100.00"));

        assertThrows(InvalidAmountException.class,
                () -> wallet.withdraw(BigDecimal.ZERO));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void withdrawShouldThrowAndKeepBalanceUnchangedWhenAmountHasFractionOfCent() {
        wallet.deposit(new BigDecimal("100.00"));

        assertThrows(InvalidAmountException.class,
                () -> wallet.withdraw(new BigDecimal("10.001")));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void withdrawShouldDecreaseBalanceWhenAmountHasTrailingZeros() {
        wallet.deposit(new BigDecimal("100.00"));

        wallet.withdraw(new BigDecimal("10.000"));
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("90.00"));
    }

    @Test
    void withdrawShouldThrowAndKeepBalanceUnchangedWhenAmountExceedsLimit() {
        wallet.deposit(new BigDecimal("100.00"));

        InvalidAmountException exception = assertThrows(
                InvalidAmountException.class,
                () -> wallet.withdraw(new BigDecimal("100000000000000000.00")));
            
        assertEquals("Maximum amount exceeded", exception.getMessage());
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
