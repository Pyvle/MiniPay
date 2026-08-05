package com.minipay.wallet;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minipay.wallet.dto.CreateWalletRequest;
import com.minipay.wallet.dto.AmountRequest;
import com.minipay.wallet.dto.TransferRequest;
import com.minipay.wallet.dto.WalletResponse;
import com.minipay.transaction.TransactionService;
import com.minipay.transaction.dto.TransactionResponse;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {
    private final WalletService walletService;
    private final TransactionService transactionService;

    public WalletController(WalletService walletService, TransactionService transactionService) {
        this.walletService = walletService;
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@Valid @RequestBody CreateWalletRequest request) {
        Wallet wallet = walletService.createWalletForUser(request.getUserId());
        return new WalletResponse(wallet);
    }

    @PostMapping("/{id}/deposit")
    public WalletResponse deposit(@PathVariable Long id, @Valid @RequestBody AmountRequest request) {
        Wallet wallet = walletService.deposit(id, request.getAmount());
        return new WalletResponse(wallet);
    }

    @PostMapping("/{id}/withdraw")
    public WalletResponse withdraw(@PathVariable Long id, @Valid @RequestBody AmountRequest request) {
        Wallet wallet = walletService.withdraw(id, request.getAmount());
        return new WalletResponse(wallet);
    }

    @PostMapping("/{fromWalletId}/transfer")
    public WalletResponse transfer(@PathVariable Long fromWalletId, @Valid @RequestBody TransferRequest request) {
        Wallet fromWallet = walletService.transfer(fromWalletId,
                request.getToWalletId(),
                request.getAmount());
        return new WalletResponse(fromWallet);
    }

    @GetMapping("/{id}")
    public WalletResponse getWalletById(@PathVariable Long id) {
        Wallet wallet = walletService.getWalletById(id);
        return new WalletResponse(wallet);
    }

    @GetMapping("/by-user/{userId}")
    public WalletResponse getWalletByUserId(@PathVariable Long userId) {
        Wallet wallet = walletService.getWalletByUserId(userId);
        return new WalletResponse(wallet);
    }

    @GetMapping("/{id}/transactions")
    public List<TransactionResponse> getTransactionByWalletId(@PathVariable Long id) {
        return transactionService.getTransactionsByWalletId(id);
    }

}
