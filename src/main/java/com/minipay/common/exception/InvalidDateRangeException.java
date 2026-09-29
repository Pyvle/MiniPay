package com.minipay.common.exception;

public class InvalidDateRangeException extends RuntimeException{
    public InvalidDateRangeException() {
        super("Start date must not be after end date");
    }
    
}
