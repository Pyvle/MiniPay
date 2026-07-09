package com.minipay.wallet;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minipay.wallet.dto.CreateWalletRequest;
import com.minipay.wallet.dto.DepositRequest;
import com.minipay.wallet.dto.WalletResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;



@RestController
@RequestMapping("/api/wallets")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse createWallet(@Valid @RequestBody CreateWalletRequest request) {
        Wallet wallet = walletService.createWalletForUser(request.getUserId());
        return new WalletResponse(wallet);
    }

    @PostMapping("/{id}/deposit")
    public WalletResponse deposit(@PathVariable Long id, @Valid @RequestBody DepositRequest request) {
        Wallet wallet = walletService.deposit(id, request.getAmount());
        return new WalletResponse(wallet);
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
    
    
    
}
