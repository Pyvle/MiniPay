package com.minipay.idempotency;

public record IdempotencyResponse(int status, String body) {
}
