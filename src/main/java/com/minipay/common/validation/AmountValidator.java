package com.minipay.common.validation;

import java.math.BigDecimal;

import com.minipay.common.exception.InvalidAmountException;

public class AmountValidator {

private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");

public static void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount must not be null");
        } else if (amount.compareTo(new BigDecimal("0.01")) < 0) {
            throw new InvalidAmountException("Amount must be at least 0.01");
        } else if(amount.compareTo(MAX_AMOUNT) > 0) {
            throw new InvalidAmountException("Maximum amount exceeded");
        }
        amount = amount.stripTrailingZeros();
        if(amount.scale() > 2) {
            throw new InvalidAmountException("Amount must not have fractions of a cent");
        }
    }
}
