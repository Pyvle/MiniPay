package com.minipay.common.exception;

public class InvalidAmountException extends RuntimeException{
    public InvalidAmountException() {
        super("Amount must be positive");
    }

    public InvalidAmountException(String message) {
        super(message);
    }
    
}
