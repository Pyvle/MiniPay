package com.minipay.idempotency;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.common.exception.IdempotencyConflictException;
import com.minipay.transaction.TransactionType;
import com.minipay.transaction.dto.TransactionResponse;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository recordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            ObjectMapper objectMapper) {
        this.recordRepository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IdempotencyResponse execute(
            UUID key,
            TransactionType operationType,
            Long walletId,
            Long toWalletId,
            BigDecimal amount,
            Supplier<TransactionResponse> operation) {
        int insertedRows = recordRepository.tryReserve(key,
                operationType.name(),
                walletId,
                toWalletId,
                amount);

        IdempotencyRecord idempotencyRecord = recordRepository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Key not found"));

        if(insertedRows == 1) {
            TransactionResponse transactionResponse = operation.get();

            String json;
            try {
                json = objectMapper.writeValueAsString(transactionResponse);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize transaction response", e);
            }

            idempotencyRecord.complete(200, json);
            return new IdempotencyResponse(200, json);
        } else {
            boolean sameRequest =  operationType == idempotencyRecord.getOperationType() &&
                Objects.equals(walletId, idempotencyRecord.getWalletId()) &&
                Objects.equals(toWalletId, idempotencyRecord.getToWalletId()) &&
                idempotencyRecord.getAmount().compareTo(amount) == 0;
            
            if(!sameRequest) {
                throw new IdempotencyConflictException("Idempotency key has already been used with different request parameters");
            }

            if(!Objects.equals(IdempotencyStatus.COMPLETED, idempotencyRecord.getStatus())) {
                throw new IdempotencyConflictException("Request is not completed");
            }

            return new IdempotencyResponse(
                idempotencyRecord.getResponseStatus(),
                idempotencyRecord.getResponseBody());
                
        }
    }
}
