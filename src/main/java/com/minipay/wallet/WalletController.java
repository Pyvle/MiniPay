package com.minipay.wallet;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minipay.wallet.dto.CreateWalletRequest;
import com.minipay.wallet.dto.AmountRequest;
import com.minipay.wallet.dto.TransferRequest;
import com.minipay.wallet.dto.WalletResponse;
import com.minipay.idempotency.IdempotencyResponse;
import com.minipay.transaction.TransactionService;
import com.minipay.transaction.dto.TransactionResponse;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ResponseEntity<String> deposit(
            @PathVariable("id") Long id,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {

        IdempotencyResponse result = walletService.deposit(
                id, request.getAmount(), idempotencyKey);

        return ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.body());
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<String> withdraw(@PathVariable("id") Long id,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {

        IdempotencyResponse result = walletService.withdraw(
                id, request.getAmount(), idempotencyKey);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.body());
    }

    @PostMapping("/{fromWalletId}/transfer")
    public ResponseEntity<String> transfer(@PathVariable("fromWalletId") Long fromWalletId,
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey) {

        IdempotencyResponse result = walletService.transfer(fromWalletId,
                request.getToWalletId(),
                request.getAmount(),
                idempotencyKey);
        return ResponseEntity.status(result.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(result.body());
    }

    @GetMapping("/{id}")
    public WalletResponse getWalletById(@PathVariable("id") Long id) {
        Wallet wallet = walletService.getWalletById(id);
        return new WalletResponse(wallet);
    }

    @GetMapping("/by-user/{userId}")
    public WalletResponse getWalletByUserId(@PathVariable("userId") Long userId) {
        Wallet wallet = walletService.getWalletByUserId(userId);
        return new WalletResponse(wallet);
    }

    @GetMapping("/{id}/transactions")
    public List<TransactionResponse> getTransactionByWalletId(@PathVariable("id") Long id) {
        return transactionService.getTransactionsByWalletId(id);
    }

}
