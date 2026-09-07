package com.minipay.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.minipay.common.exception.InsufficientBalanceException;
import com.minipay.common.exception.InvalidAmountException;
import com.minipay.common.exception.SelfTransferException;
import com.minipay.common.exception.UserNotFoundException;
import com.minipay.common.exception.WalletAlreadyExistsException;
import com.minipay.common.exception.WalletNotFoundException;
import com.minipay.transaction.Transaction;
import com.minipay.transaction.TransactionRepository;
import com.minipay.transaction.TransactionStatus;
import com.minipay.transaction.TransactionType;
import com.minipay.user.UserRepository;
import com.minipay.user.User;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletService walletService;

    // createWalletForUser
    @Test
    void createWalletForUserShouldCreateWallet() {

        Long userId = 1L;
        User user = new User("Ivan", "Ivan@email.com");

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(walletRepository.existsByUserId(userId))
                .thenReturn(false);

        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Wallet result = walletService.createWalletForUser(userId);

        assertNotNull(result);
        assertSame(user, result.getUser());

        assertThat(result.getBalance())
                .isEqualByComparingTo("0.00");

        verify(userRepository).findById(userId);
        verify(walletRepository).existsByUserId(userId);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);

        verify(walletRepository).save(captor.capture());

        Wallet savedWallet = captor.getValue();

        assertSame(result, savedWallet);

    }

    @Test
    void createWalletForUserShouldThrowWhenUserNotFound() {

        when(userRepository.findById(999L))
                .thenReturn(Optional.empty());

        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> walletService.createWalletForUser(999L));

        assertEquals("User not found: 999", exception.getMessage());
        verify(walletRepository, never()).existsByUserId(any());
        verify(walletRepository, never()).save(any());
    }

    @Test
    void createWalletForUserShouldThrowWhenWalletAlreadyExists() {
        when(userRepository.findById(999L))
                .thenReturn(Optional.of(new User()));

        when(walletRepository.existsByUserId(999L))
                .thenReturn(true);

        WalletAlreadyExistsException exception = assertThrows(WalletAlreadyExistsException.class,
                () -> walletService.createWalletForUser(999L));

        assertEquals("Wallet already exists for user: 999", exception.getMessage());
        verify(walletRepository, never()).save(any(Wallet.class));
    }

    // getWalletById
    @Test
    void getWalletByIdShouldReturnWallet() {
        Wallet wallet = new Wallet();

        when(walletRepository.findById(1L))
                .thenReturn(Optional.of(wallet));

        Wallet result = walletService.getWalletById(1L);

        assertSame(wallet, result);
        verify(walletRepository).findById(1L);
    }

    @Test
    void getWalletByIdShouldThrowWhenWalletDoesNotExist() {
        when(walletRepository.findById(999L))
                .thenReturn(Optional.empty());

        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class,
                () -> walletService.getWalletById(999L));

        assertEquals("Wallet not found: 999", exception.getMessage());
    }

    // getWalletByUserId
    @Test
    void getWalletByUserIdShouldReturnWallet() {
        Wallet wallet = new Wallet();

        when(walletRepository.findByUserId(1L))
                .thenReturn(Optional.of(wallet));

        Wallet result = walletService.getWalletByUserId(1L);

        assertSame(wallet, result);
        verify(walletRepository).findByUserId(1L);
    }

    @Test
    void getWalletByUserIdShouldThrowWhenNotFound() {
        when(walletRepository.findByUserId(999L))
                .thenReturn(Optional.empty());

        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class,
                () -> walletService.getWalletByUserId(999L));

        assertEquals("Wallet not found for user: 999", exception.getMessage());
    }

    // deposit
    @Test
    void depositShouldIncreaseBalanceAndSaveTransaction() {
        Wallet wallet = new Wallet();

        when(walletRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(wallet));

        Wallet result = walletService.deposit(1L, new BigDecimal("100.00"));

        assertSame(wallet, result);
        assertThat(result.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));

        verify(walletRepository).save(result);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository).save(captor.capture());

        Transaction saveTransaction = captor.getValue();

        assertNull(saveTransaction.getFromWallet());
        assertSame(result, saveTransaction.getToWallet());
        assertEquals(TransactionType.DEPOSIT, saveTransaction.getType());
        assertEquals(TransactionStatus.SUCCESS, saveTransaction.getStatus());
        assertThat(saveTransaction.getAmount())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.00", "-0.01", "-100" })
    void depositShouldThrowWhenAmountIsNotPositive(String value) {

        InvalidAmountException exception = assertThrows(InvalidAmountException.class,
                () -> walletService.deposit(1L, new BigDecimal(value)));

        assertEquals("Amount must be at least 0.01", exception.getMessage());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void depositShouldThrowWhenAmountIsNull() {
        InvalidAmountException exception = assertThrows(
                InvalidAmountException.class,
                () -> walletService.deposit(1L, null));

        assertEquals("Amount must not be null", exception.getMessage());

        verify(walletRepository, never()).findByIdForUpdate(anyLong());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void depositShouldThrowWhenWalletNotFound() {

        when(walletRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class,
                () -> walletService.deposit(999L, new BigDecimal("100.00")));

        assertEquals("Wallet not found: 999", exception.getMessage());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // withdraw
    @Test
    void withdrawShouldDecreaseBalanceAndSaveTransaction() {
        Wallet wallet = new Wallet();
        wallet.deposit(new BigDecimal("200.00"));

        when(walletRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(wallet));

        Wallet result = walletService.withdraw(1L, new BigDecimal("100.00"));

        assertSame(wallet, result);
        assertThat(result.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));

        verify(walletRepository).save(result);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository).save(captor.capture());

        Transaction saveTransaction = captor.getValue();

        assertNull(saveTransaction.getToWallet());
        assertSame(result, saveTransaction.getFromWallet());
        assertEquals(TransactionType.WITHDRAWAL, saveTransaction.getType());
        assertEquals(TransactionStatus.SUCCESS, saveTransaction.getStatus());
        assertThat(saveTransaction.getAmount())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void withdrawShouldThrowWhenWalletNotFound() {
        when(walletRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class,
                () -> walletService.withdraw(999L, new BigDecimal("100.00")));

        assertEquals("Wallet not found: 999", exception.getMessage());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.00", "-0.01", "-100" })
    void withdrawShouldThrowWhenAmountIsNotPositive(String value) {

        InvalidAmountException exception = assertThrows(InvalidAmountException.class,
                () -> walletService.withdraw(1L, new BigDecimal(value)));

        assertEquals("Amount must be at least 0.01", exception.getMessage());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void withdrawShouldThrowWhenAmountIsNull() {
        InvalidAmountException exception = assertThrows(
                InvalidAmountException.class,
                () -> walletService.withdraw(1L, null));

        assertEquals("Amount must not be null", exception.getMessage());

        verify(walletRepository, never()).findByIdForUpdate(anyLong());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdrawShouldThrowWhenBalanceIsInsufficient() {
        Wallet wallet = new Wallet();
        wallet.deposit(new BigDecimal("200.00"));

        when(walletRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(wallet));

        InsufficientBalanceException exception = assertThrows(InsufficientBalanceException.class,
                () -> walletService.withdraw(1L, new BigDecimal("500.00")));

        assertEquals("Insufficient balance", exception.getMessage());
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("200.00"));
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void withdrawShouldAllowWithdrawingEntireBalance() {
        Wallet wallet = new Wallet();
        wallet.deposit(new BigDecimal("100.00"));

        when(walletRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(wallet));

        Wallet result = walletService.withdraw(1L, new BigDecimal("100.00"));

        assertSame(wallet, result);
        verify(walletRepository).save(wallet);
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        verify(transactionRepository).save(any(Transaction.class));
    }

    // transfer
    @Test
    void transferShouldMoveMoneyAndSaveTransaction() {
        Long fromWalletId = 1L;
        Long toWalletId = 2L;
        Wallet fromWallet = new Wallet();
        Wallet toWallet = new Wallet();

        fromWallet.deposit(new BigDecimal("300.00"));

        when(walletRepository.findByIdForUpdate(fromWalletId))
                .thenReturn(Optional.of(fromWallet));

        when(walletRepository.findByIdForUpdate(toWalletId))
                .thenReturn(Optional.of(toWallet));

        Wallet result = walletService.transfer(fromWalletId, toWalletId, new BigDecimal("100.00"));

        assertSame(fromWallet, result);
        assertThat(fromWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(toWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));

        verify(walletRepository).save(fromWallet);
        verify(walletRepository).save(toWallet);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        verify(transactionRepository).save(captor.capture());

        Transaction saveTransaction = captor.getValue();

        assertSame(fromWallet, saveTransaction.getFromWallet());
        assertSame(toWallet, saveTransaction.getToWallet());
        assertEquals(TransactionType.TRANSFER, saveTransaction.getType());
        assertEquals(TransactionStatus.SUCCESS, saveTransaction.getStatus());
        assertThat(saveTransaction.getAmount())
                .isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void transferShouldThrowWhenSourceWalletNotFound() {
        when(walletRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(new Wallet()));

        when(walletRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class,
                () -> walletService.transfer(999L, 1L, new BigDecimal("100.00")));

        assertEquals("Wallet not found: 999", exception.getMessage());
        verify(walletRepository).findByIdForUpdate(1L);
        verify(walletRepository).findByIdForUpdate(999L);
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transferShouldThrowWhenDestinationWalletNotFound() {
        Wallet wallet = new Wallet();

        when(walletRepository.findByIdForUpdate(999L))
                .thenReturn((Optional.empty()));
        when(walletRepository.findByIdForUpdate(1L))
                .thenReturn((Optional.of(wallet)));

        WalletNotFoundException exception = assertThrows(WalletNotFoundException.class,
                () -> walletService.transfer(1L, 999L, new BigDecimal("100.00")));

        assertEquals("Wallet not found: 999", exception.getMessage());
        verify(walletRepository).findByIdForUpdate(1L);
        verify(walletRepository).findByIdForUpdate(999L);
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transferShouldThrowWhenWalletsAreSame() {

        SelfTransferException exception = assertThrows(SelfTransferException.class,
                () -> walletService.transfer(1L, 1L, new BigDecimal("100.00")));

        assertEquals("Cannot transfer to the same wallet: 1", exception.getMessage());
        verify(walletRepository, never()).findByIdForUpdate(1L);
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0.00", "-0.01", "-100" })
    void transferShouldThrowWhenAmountIsNotPositive(String value) {

        InvalidAmountException exception = assertThrows(InvalidAmountException.class,
                () -> walletService.transfer(1L, 2L, new BigDecimal(value)));

        assertEquals("Amount must be at least 0.01", exception.getMessage());
        verify(walletRepository, never()).findByIdForUpdate(1L);
        verify(walletRepository, never()).findByIdForUpdate(2L);
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transferShouldThrowWhenAmountIsNull() {
        InvalidAmountException exception = assertThrows(
                InvalidAmountException.class,
                () -> walletService.transfer(1L, 2L, null));

        assertEquals("Amount must not be null", exception.getMessage());

        verify(walletRepository, never()).findByIdForUpdate(anyLong());
        verify(walletRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void transferShouldThrowWhenAmountHasMoreThanTwoSignificantDecimalPlaces() {
        InvalidAmountException exception = assertThrows(
                InvalidAmountException.class,
                () -> walletService.transfer(1L, 2L, new BigDecimal("10.001")));

        assertEquals("Amount must not have fractions of a cent", exception.getMessage());

        verify(walletRepository, never()).findByIdForUpdate(anyLong());
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void transferShouldThrowWhenBalanceIsInsufficient() {
        Wallet wallet = new Wallet();
        Wallet wallet2 = new Wallet();
        wallet.deposit(new BigDecimal("100.00"));

        when(walletRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(wallet2));

        InsufficientBalanceException exception = assertThrows(InsufficientBalanceException.class,
                () -> walletService.transfer(1L, 2L, new BigDecimal("500.00")));

        assertEquals("Insufficient balance", exception.getMessage());
        assertThat(wallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(wallet2.getBalance())
                .isEqualByComparingTo(BigDecimal.ZERO);
        verify(walletRepository, never()).save(any(Wallet.class));
        verify(transactionRepository, never()).save(any(Transaction.class));

    }

    @Test
    void transferShouldAllowTransferringEntireBalance() {
        Wallet wallet = new Wallet();
        wallet.deposit(new BigDecimal("100.00"));
        Wallet wallet2 = new Wallet();

        when(walletRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(wallet2));

        Wallet result = walletService.transfer(1L, 2L, new BigDecimal("100.00"));

        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(wallet2.getBalance()).isEqualByComparingTo("100.00");
        assertSame(wallet, result);
        verify(walletRepository).save(wallet);
        verify(walletRepository).save(wallet2);
        verify(transactionRepository).save(any(Transaction.class));
    }
}
