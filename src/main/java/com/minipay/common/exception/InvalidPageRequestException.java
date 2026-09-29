package com.minipay.common.exception;

public class InvalidPageRequestException extends RuntimeException {
    public InvalidPageRequestException() {
        super("Page must be at least 0 and size must be between 1 and 100");
    }
}
