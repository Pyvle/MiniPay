package com.minipay.common.exception;

public class SelfTransferException extends RuntimeException {

    public SelfTransferException(Long walletId) {
        super("Cannot transfer to the same wallet: " + walletId);
    }
    
}
