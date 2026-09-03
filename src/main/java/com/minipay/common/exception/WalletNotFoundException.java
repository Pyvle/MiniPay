package com.minipay.common.exception;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(Long walletId) {
        super("Wallet not found: " + walletId);
    }

    private WalletNotFoundException(String message) {
        super(message);
    }

    public static WalletNotFoundException forUserId(Long userId) {
        return new WalletNotFoundException(
            "Wallet not found for user: " + userId);
    }
    
}
