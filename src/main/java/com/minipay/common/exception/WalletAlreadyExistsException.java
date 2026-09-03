package com.minipay.common.exception;

public class WalletAlreadyExistsException extends RuntimeException {
    
    public WalletAlreadyExistsException(Long userId) {
        super("Wallet already exists for user: "  + userId);
    }
}
