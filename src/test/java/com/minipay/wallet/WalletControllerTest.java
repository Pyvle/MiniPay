package com.minipay.wallet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import com.minipay.idempotency.IdempotencyResponse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.minipay.common.exception.BalanceLimitExceededException;
import com.minipay.common.exception.IdempotencyConflictException;
import com.minipay.common.exception.InsufficientBalanceException;
import com.minipay.common.exception.InvalidAmountException;
import com.minipay.common.exception.SelfTransferException;
import com.minipay.common.exception.UserNotFoundException;
import com.minipay.common.exception.WalletAlreadyExistsException;
import com.minipay.common.exception.WalletNotFoundException;
import com.minipay.transaction.Transaction;
import com.minipay.transaction.TransactionService;
import com.minipay.transaction.dto.TransactionResponse;
import com.minipay.user.User;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;
    @MockitoBean
    private TransactionService transactionService;

    @Test
    void createWalletShouldReturnCreatedWallet() throws Exception {
        User user = mock(User.class);
        Wallet wallet = mock(Wallet.class);

        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("John Doe");
        when(user.getEmail()).thenReturn("john@example.com");
        when(wallet.getUser()).thenReturn(user);
        when(wallet.getBalance()).thenReturn(BigDecimal.ZERO);

        when(wallet.getId()).thenReturn(10L);

        when(walletService.createWalletForUser(1L))
                .thenReturn(wallet);

        mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                            "userId": 1
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.userName").value("John Doe"))
                .andExpect(jsonPath("$.userEmail").value("john@example.com"))
                .andExpect(jsonPath("$.balance").value(BigDecimal.ZERO));

        verify(walletService).createWalletForUser(1L);
    }

    @Test
    void createWalletShouldReturnBadRequestWhenUserIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("userId:")));

        verifyNoInteractions(walletService);
    }

    @Test
    void createWalletShouldReturnNotFoundWhenUserNotFound() throws Exception {
        when(walletService.createWalletForUser(999L))
                .thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "userId": 999
                        }
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found: 999"));

        verify(walletService).createWalletForUser(999L);
    }

    @Test
    void createWalletShouldReturnConflictWhenWalletAlreadyExists() throws Exception {
        when(walletService.createWalletForUser(999L))
                .thenThrow(new WalletAlreadyExistsException(999L));

        mockMvc.perform(post("/api/wallets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "userId": 999
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Wallet already exists for user: 999"));

        verify(walletService).createWalletForUser(999L);
    }

    @Test
    void getWalletByIdShouldReturnWallet() throws Exception {
        User user = mock(User.class);
        Wallet wallet = mock(Wallet.class);

        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("John Doe");
        when(user.getEmail()).thenReturn("john@example.com");

        when(wallet.getId()).thenReturn(10L);
        when(wallet.getUser()).thenReturn(user);
        when(wallet.getBalance()).thenReturn(new BigDecimal("100.00"));

        when(walletService.getWalletById(10L))
                .thenReturn(wallet);

        mockMvc.perform(get("/api/wallets/{id}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.userName").value("John Doe"))
                .andExpect(jsonPath("$.userEmail").value("john@example.com"))
                .andExpect(jsonPath("$.balance").value(100.00));

        verify(walletService).getWalletById(10L);
    }

    @Test
    void getWalletByIdShouldReturnNotFoundWhenWalletNotFound() throws Exception {
        when(walletService.getWalletById(999L))
                .thenThrow(new WalletNotFoundException(999L));

        mockMvc.perform(get("/api/wallets/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Wallet not found: 999"));

        verify(walletService).getWalletById(999L);
    }

    @Test
    void getWalletByUserIdShouldReturnWallet() throws Exception {
        User user = mock(User.class);
        Wallet wallet = mock(Wallet.class);

        when(user.getId()).thenReturn(1L);
        when(user.getName()).thenReturn("John Doe");
        when(user.getEmail()).thenReturn("john@example.com");

        when(wallet.getId()).thenReturn(10L);
        when(wallet.getUser()).thenReturn(user);
        when(wallet.getBalance()).thenReturn(new BigDecimal("100.00"));

        when(walletService.getWalletByUserId(1L))
                .thenReturn(wallet);

        mockMvc.perform(get("/api/wallets/by-user/{userId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.userName").value("John Doe"))
                .andExpect(jsonPath("$.userEmail").value("john@example.com"))
                .andExpect(jsonPath("$.balance").value(100.00));

        verify(walletService).getWalletByUserId(1L);
    }

    @Test
    void getWalletByUserIdShouldReturnNotFoundWhenWalletNotFound()
            throws Exception {

        when(walletService.getWalletByUserId(999L))
                .thenThrow(WalletNotFoundException.forUserId(999L));

        mockMvc.perform(get("/api/wallets/by-user/{userId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Wallet not found for user: 999"));

        verify(walletService).getWalletByUserId(999L);
    }

    @Test
    void depositShouldReturnUpdatedWallet() throws Exception {
        UUID key = UUID.randomUUID();

        String json = """
                {"id":10,"userId":1,"userName":"John Doe","userEmail":"john@example.com","balance":100.00,"createdAt":null}
                """;

        when(walletService.deposit(10L, new BigDecimal("100.00"), key))
                .thenReturn(new IdempotencyResponse(200, json));

        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 100.00
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(json))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.balance").value(100.00));

        verify(walletService)
                .deposit(10L, new BigDecimal("100.00"), key);
    }

    @Test
    void depositShouldReturnBadRequestWhenAmountIsInvalid() throws Exception {
        UUID key = UUID.randomUUID();

        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 0.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("amount:")));

        verifyNoInteractions(walletService);
    }

    @Test
    void depositShouldReturnNotFoundWhenServiceThrows() throws Exception {
        UUID key = UUID.randomUUID();
        when(walletService.deposit(999L, new BigDecimal("100.00"), key))
                .thenThrow(new WalletNotFoundException(999L));

        mockMvc.perform(post("/api/wallets/{id}/deposit", 999L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 100.00
                        }
                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Wallet not found: 999"));

        verify(walletService)
                .deposit(999L, new BigDecimal("100.00"), key);
    }

    @Test
    void depositShouldReturnBadRequestWhenAmountIsMissing() throws Exception {
        UUID key = UUID.randomUUID();

        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(containsString("amount:")));

        verifyNoInteractions(walletService);
    }

    @Test
    void depositShouldReturnBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 100.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Missing required header: Idempotency-Key"));

        verifyNoInteractions(walletService);
    }

    @Test
    void depositShouldReturnBadRequestWhenIdempotencyKeyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
                .header("Idempotency-Key", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 100.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));

        verifyNoInteractions(walletService);
    }

    @Test
    void depositShouldReturnBadRequestWhenServiceThrowsInvalidAmountException()
            throws Exception {
        UUID key = UUID.randomUUID();
        when(walletService.deposit(10L, new BigDecimal("500.00"), key))
                .thenThrow(new InvalidAmountException());

        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 500.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Amount must be positive"));
    }

    @Test
    void depositShouldReturnConflictWhenServiceThrowsBalanceLimitExceededException()
            throws Exception {
        UUID key = UUID.randomUUID();
        when(walletService.deposit(10L, new BigDecimal("0.01"), key))
                .thenThrow(new BalanceLimitExceededException());

        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 0.01
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Maximum wallet balance exceeded"));
    }

    @Test
    void withdrawShouldReturnUpdatedWallet() throws Exception {
        UUID key = UUID.randomUUID();
        String json = """
                {"id":10,"userId":1,"userName":"John Doe","userEmail":"john@example.com","balance":50.00,"createdAt":null}
                """;

        when(walletService.withdraw(10L, new BigDecimal("50.00"), key))
                .thenReturn(new IdempotencyResponse(200, json));

        mockMvc.perform(post("/api/wallets/{id}/withdraw", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 50.00
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(json))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.balance").value(50.00));

        verify(walletService)
                .withdraw(10L, new BigDecimal("50.00"), key);
    }

    @Test
    void withdrawShouldReturnBadRequestWhenAmountIsInvalid() throws Exception {
        UUID key = UUID.randomUUID();
        mockMvc.perform(post("/api/wallets/{id}/withdraw", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": -10.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("amount:")));

        verifyNoInteractions(walletService);
    }

    @Test
    void withdrawShouldReturnBadRequestWhenBalanceIsInsufficient()
            throws Exception {
        UUID key = UUID.randomUUID();

        when(walletService.withdraw(10L, new BigDecimal("500.00"), key))
                .thenThrow(new InsufficientBalanceException());

        mockMvc.perform(post("/api/wallets/{id}/withdraw", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 500.00
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Insufficient balance"));

        verify(walletService)
                .withdraw(10L, new BigDecimal("500.00"), key);
    }

    @Test
    void withdrawShouldReturnBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/wallets/{id}/withdraw", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 50.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Missing required header: Idempotency-Key"));

        verifyNoInteractions(walletService);
    }

    @Test
    void withdrawShouldReturnBadRequestWhenIdempotencyKeyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/wallets/{id}/withdraw", 10L)
                .header("Idempotency-Key", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 50.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));

        verifyNoInteractions(walletService);
    }

    @Test
    void transferShouldReturnUpdatedSourceWallet() throws Exception {
        UUID key = UUID.randomUUID();
        String json = """
                {"id":10,"userId":1,"userName":"John Doe","userEmail":"john@example.com","balance":50.00,"createdAt":null}
                """;

        when(walletService.transfer(
                10L,
                20L,
                new BigDecimal("50.00"), key)).thenReturn(new IdempotencyResponse(200, json));

        mockMvc.perform(post("/api/wallets/{fromWalletId}/transfer", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "toWalletId": 20,
                          "amount": 50.00
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(json))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.balance").value(50.00));

        verify(walletService).transfer(
                10L,
                20L,
                new BigDecimal("50.00"), key);
    }

    @Test
    void transferShouldReturnBadRequestWhenAmountIsInvalid() throws Exception {
        UUID key = UUID.randomUUID();
        mockMvc.perform(post("/api/wallets/{fromWalletId}/transfer", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "toWalletId": 20,
                          "amount": 0.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("amount:")));

        verifyNoInteractions(walletService);
    }

    @Test
    void transferShouldReturnBadRequestWhenToWalletIdIsMissing()
            throws Exception {
        UUID key = UUID.randomUUID();

        mockMvc.perform(post("/api/wallets/{fromWalletId}/transfer", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 50.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("toWalletId:")));

        verifyNoInteractions(walletService);
    }

    @Test
    void transferShouldReturnBadRequestWhenWalletsAreSame()
            throws Exception {
        UUID key = UUID.randomUUID();

        when(walletService.transfer(
                10L,
                10L,
                new BigDecimal("50.00"), key)).thenThrow(new SelfTransferException(10L));

        mockMvc.perform(post("/api/wallets/{fromWalletId}/transfer", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "toWalletId": 10,
                          "amount": 50.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Cannot transfer to the same wallet: 10"));

        verify(walletService).transfer(
                10L,
                10L,
                new BigDecimal("50.00"), key);
    }

    @Test
    void transferShouldReturnBadRequestWhenBalanceIsInsufficient()
            throws Exception {
        UUID key = UUID.randomUUID();

        when(walletService.transfer(
                10L,
                20L,
                new BigDecimal("500.00"), key)).thenThrow(new InsufficientBalanceException());

        mockMvc.perform(post("/api/wallets/{fromWalletId}/transfer", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "toWalletId": 20,
                          "amount": 500.00
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Insufficient balance"));

        verify(walletService).transfer(
                10L,
                20L,
                new BigDecimal("500.00"), key);
    }

    @Test
    void transferShouldReturnBadRequestWhenAmountIsMissing()
            throws Exception {
        UUID key = UUID.randomUUID();

        mockMvc.perform(post("/api/wallets/{fromWalletId}/transfer", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "toWalletId": 20
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(containsString("amount:")));

        verifyNoInteractions(walletService);
    }

    @Test
    void transferShouldReturnBadRequestWhenIdempotencyKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/wallets/{fromWalletId}/transfer", 10L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "toWalletId": 20,
                          "amount": 50.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Missing required header: Idempotency-Key"));

        verifyNoInteractions(walletService);
    }

    @Test
    void transferShouldReturnBadRequestWhenIdempotencyKeyIsInvalid() throws Exception {
        mockMvc.perform(post("/api/wallets/{fromWalletId}/transfer", 10L)
                .header("Idempotency-Key", "not-a-uuid")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "toWalletId": 20,
                          "amount": 50.00
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));

        verifyNoInteractions(walletService);
    }

    @Test
    void getTransactionsShouldReturnTransactions() throws Exception {
        Wallet wallet = new Wallet();

        Transaction deposit = Transaction.deposit(
                wallet,
                new BigDecimal("100.00"));

        Transaction withdraw = Transaction.withdraw(
                wallet,
                new BigDecimal("40.00"));

        List<TransactionResponse> transactions = List.of(
                new TransactionResponse(deposit),
                new TransactionResponse(withdraw));

        when(transactionService.getTransactionsByWalletId(10L))
                .thenReturn(transactions);

        mockMvc.perform(get("/api/wallets/{id}/transactions", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].amount").value(100.00))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[1].amount").value(40.00))
                .andExpect(jsonPath("$[1].type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$[1].status").value("SUCCESS"));

        verify(transactionService).getTransactionsByWalletId(10L);
    }

    @Test
    void getTransactionsShouldReturnEmptyList() throws Exception {
        when(transactionService.getTransactionsByWalletId(10L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/wallets/{id}/transactions", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(transactionService).getTransactionsByWalletId(10L);
    }

    @Test
    void getTransactionsShouldReturnNotFoundWhenWalletNotFound()
            throws Exception {

        when(transactionService.getTransactionsByWalletId(999L))
                .thenThrow(new WalletNotFoundException(999L));

        mockMvc.perform(get("/api/wallets/{id}/transactions", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Wallet not found: 999"));

        verify(transactionService).getTransactionsByWalletId(999L);
    }

    @Test
    void depositShouldReturnConflictWhenWalletLockCannotBeAcquired()
            throws Exception {
                UUID key = UUID.randomUUID();
        when(walletService.deposit(10L, new BigDecimal("20.00"), key))
                .thenThrow(new CannotAcquireLockException(
                        "Technical database details"));

        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
        .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 20.00
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Operation conflicted with another request. Please try again."))
                .andExpect(jsonPath("$.path").value("/api/wallets/10/deposit"));

        verify(walletService).deposit(10L, new BigDecimal("20.00"), key);
    }

    @Test
    void depositShouldReturnConflictWhenIdempotencyKeyIsReusedWithDifferentParameters()
            throws Exception {
        UUID key = UUID.randomUUID();
        String message = "Idempotency key has already been used with different request parameters";

        when(walletService.deposit(10L, new BigDecimal("20.00"), key))
                .thenThrow(new IdempotencyConflictException(message));

        mockMvc.perform(post("/api/wallets/{id}/deposit", 10L)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "amount": 20.00
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value("/api/wallets/10/deposit"));

        verify(walletService).deposit(10L, new BigDecimal("20.00"), key);
    }

}
