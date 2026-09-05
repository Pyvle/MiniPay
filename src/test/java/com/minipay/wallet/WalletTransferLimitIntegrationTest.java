package com.minipay.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.minipay.common.exception.BalanceLimitExceededException;
import com.minipay.support.PostgresTestConfiguration;
import com.minipay.transaction.TransactionRepository;
import com.minipay.user.User;
import com.minipay.user.UserRepository;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Import(PostgresTestConfiguration.class)
class WalletTransferLimitIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void transferShouldRollbackWhenRecipientBalanceLimitIsExceeded() {
        User savedFromUser = userRepository.saveAndFlush(
                new User("Ivan", "ivan@example.com"));
        User savedToUser = userRepository.saveAndFlush(
                new User("Obama", "obama@example.com"));
        Wallet fromWallet = new Wallet(savedFromUser);
        Wallet toWallet = new Wallet(savedToUser);

        fromWallet.deposit(new BigDecimal("100.00"));
        toWallet.deposit(new BigDecimal("99999999999999999.99"));
        fromWallet = walletRepository.saveAndFlush(fromWallet);
        toWallet = walletRepository.saveAndFlush(toWallet);

        Long fromWalletId = fromWallet.getId();
        Long toWalletId = toWallet.getId();

        Long transferCount = transactionRepository.count();

        assertThrows(BalanceLimitExceededException.class, 
            () -> walletService.transfer(fromWalletId, toWalletId, new BigDecimal("0.01")));

        Wallet persistedFromWallet = 
            walletRepository.findById(fromWalletId).orElseThrow();
        Wallet persistedToWallet = 
            walletRepository.findById(toWalletId).orElseThrow();

        assertThat(persistedFromWallet.getBalance())
            .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(persistedToWallet.getBalance())
            .isEqualByComparingTo(new BigDecimal("99999999999999999.99"));
        assertThat(transactionRepository.count()).isEqualTo(transferCount);
    }

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }

}
