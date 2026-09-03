package com.minipay.common.exception;

public class InsufficientBalanceException extends RuntimeException{

    public InsufficientBalanceException() {
        super("Insufficient balance");
    }
    
}
