package com.minipay.wallet;

import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.minipay.support.PostgresTestConfiguration;
import com.minipay.transaction.Transaction;
import com.minipay.transaction.TransactionRepository;
import com.minipay.transaction.TransactionStatus;
import com.minipay.transaction.TransactionType;
import com.minipay.user.User;
import com.minipay.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@Transactional
@Import(PostgresTestConfiguration.class)
class WalletServiceIntegrationTest {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"-1,20", "0,0", "0,-1", "0,101", "abc,20", "0,abc"})
    void historyShouldReturnBadRequestForInvalidPageParameters(String page, String size) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/wallets/" + wallet.getId() + "/transactions").param("page", page).param("size", size))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(400))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("Bad Request"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.path")
                        .value("/api/wallets/" + wallet.getId() + "/transactions"));
    }

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"deposit,DEPOSIT", "withdraw,WITHDRAWAL", "transfer,TRANSFER"})
    void operationResponseShouldIdentifyPersistedTransactionAndReplayOriginalJson(
            String endpoint, TransactionType type) throws Exception {
        wallet.deposit(new BigDecimal("100.00"));
        walletRepository.saveAndFlush(wallet);
        User recipient = userRepository.saveAndFlush(new User("Recipient", "recipient@example.com"));
        Wallet recipientWallet = walletRepository.saveAndFlush(new Wallet(recipient));
        UUID key = UUID.randomUUID();
        String requestBody = type == TransactionType.TRANSFER
                ? "{\"amount\":20.00,\"toWalletId\":" + recipientWallet.getId() + "}"
                : "{\"amount\":20.00}";
        String url = "/api/wallets/" + wallet.getId() + "/" + endpoint;
        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Idempotency-Key", key.toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse();
        var body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("id").isIntegralNumber()).isTrue();
        assertThat(body.get("amount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(body.get("type").asText()).isEqualTo(type.name());
        assertThat(body.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(body.get("createdAt").isNull()).isFalse();
        assertThat(body.has("balance")).isFalse();
        if (type == TransactionType.DEPOSIT) {
            assertThat(body.get("fromWalletId").isNull()).isTrue();
            assertThat(body.get("toWalletId").asLong()).isEqualTo(wallet.getId());
        } else {
            assertThat(body.get("fromWalletId").asLong()).isEqualTo(wallet.getId());
            if (type == TransactionType.WITHDRAWAL) {
                assertThat(body.get("toWalletId").isNull()).isTrue();
            } else {
                assertThat(body.get("toWalletId").asLong()).isEqualTo(recipientWallet.getId());
            }
        }
        entityManager.flush();
        entityManager.clear();
        var fetched = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/transactions/" + body.get("id").asLong()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse();
        var fetchedBody = objectMapper.readTree(fetched.getContentAsString());
        for (String field : List.of("id", "fromWalletId", "toWalletId", "type", "status")) {
            assertThat(fetchedBody.get(field)).isEqualTo(body.get(field));
        }
        assertThat(fetchedBody.get("amount").decimalValue()).isEqualByComparingTo(body.get("amount").decimalValue());
        assertThat(fetchedBody.get("createdAt").isNull()).isFalse();

        walletService.deposit(wallet.getId(), new BigDecimal("5.00"), UUID.randomUUID());
        var repeated = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .header("Idempotency-Key", key.toString())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(requestBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse();
        assertThat(repeated.getContentAsString()).isEqualTo(response.getContentAsString());
        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(walletRepository.findById(wallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(type == TransactionType.DEPOSIT ? "125.00" : "85.00");
        assertThat(walletRepository.findById(recipientWallet.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo(type == TransactionType.TRANSFER ? "20.00" : "0.00");
    }


    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Wallet wallet;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(
                new User("Ivan", "ivan@example.com"));

        wallet = walletRepository.saveAndFlush(new Wallet(user));
    }

    @Test
    void depositShouldPersistBalanceAndTransaction() {
        UUID key = UUID.randomUUID();
        walletService.deposit(wallet.getId(), new BigDecimal("100.00"), key);

        entityManager.flush();
        entityManager.clear();

        Wallet persistedWallet = walletRepository.findById(wallet.getId()).orElseThrow();

        List<Transaction> transactions = transactionRepository
                .findHistory(wallet.getId(), null, null, Pageable.unpaged(), null, null).getContent();

        assertThat(persistedWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(transactions).hasSize(1);
        assertEquals(TransactionType.DEPOSIT,
                transactions.get(0).getType());
        assertEquals(TransactionStatus.SUCCESS,
                transactions.get(0).getStatus());
        assertThat(transactions.get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertNull(transactions.get(0).getFromWallet());
        assertEquals(persistedWallet.getId(),
                transactions.get(0).getToWallet().getId());
    }

    @Test
    void transferShouldPersistBalancesAndTransaction() {
        UUID key = UUID.randomUUID();
        wallet.deposit(new BigDecimal("300.00"));
        walletRepository.saveAndFlush(wallet);

        User savedToUser = userRepository.saveAndFlush(
                new User("Obama", "obama@example.com"));
        Wallet toWallet = walletRepository.saveAndFlush(
                new Wallet(savedToUser));

        Long fromWalletId = wallet.getId();
        Long toWalletId = toWallet.getId();

        walletService.transfer(fromWalletId, toWalletId, new BigDecimal("100.00"), key);

        entityManager.flush();
        entityManager.clear();

        Wallet persistedFromWallet = walletRepository.findById(fromWalletId).orElseThrow();
        Wallet persistedToWallet = walletRepository.findById(toWalletId).orElseThrow();

        assertThat(persistedFromWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(persistedToWallet.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));

        List<Transaction> transactions = transactionRepository
                .findHistory(fromWalletId, null, null, Pageable.unpaged(), null, null).getContent();

        assertThat(transactions).hasSize(1);
        assertEquals(TransactionType.TRANSFER, transactions.get(0).getType());
        assertEquals(TransactionStatus.SUCCESS, transactions.get(0).getStatus());
        assertThat(transactions.get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertEquals(persistedFromWallet.getId(), transactions.get(0).getFromWallet().getId());
        assertEquals(persistedToWallet.getId(), transactions.get(0).getToWallet().getId());
    }
}
