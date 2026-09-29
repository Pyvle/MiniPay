package com.minipay.transaction;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.minipay.common.exception.TransactionNotFoundException;
import com.minipay.wallet.Wallet;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Test
    void getTransactionShouldRejectNonNumericId() throws Exception {
        mockMvc.perform(get("/api/transactions/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid request parameter"))
                .andExpect(jsonPath("$.path").value("/api/transactions/abc"));
        org.mockito.Mockito.verifyNoInteractions(transactionService);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void getTransactionByIdShpuldReturnTransaction() throws Exception {
        Wallet wallet = new Wallet();

        Transaction transaction = Transaction.deposit(
                wallet,
                new BigDecimal("100.00"));

        when(transactionService.getTransactionById(1L))
                .thenReturn(transaction);

        mockMvc.perform(get("/api/transactions/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(transactionService).getTransactionById(1L);
    }

    @Test
    void getTransactionByIdShpuldReturnNotFoundWhenTransactionNotFound() throws Exception {
        when(transactionService.getTransactionById(999L))
                .thenThrow(new TransactionNotFoundException(999L));

        mockMvc.perform(get("/api/transactions/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transaction not found: 999"));

        verify(transactionService).getTransactionById(999L);
    }

}
