package com.minipay.common.exception;

public class BalanceLimitExceededException extends RuntimeException{

    public BalanceLimitExceededException() {
        super("Maximum wallet balance exceeded");
    }
    
}
