package com.minipay.idempotency;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository 
    extends JpaRepository<IdempotencyRecord, UUID> {
    
    @Modifying 
    @Query(value = """
        INSERT INTO idempotency_records (
            idempotency_key, operation_type, wallet_id,
            to_wallet_id, amount, status
        )
        VALUES (
            :key, :operationType, :walletId,
            :toWalletId, :amount, 'PROCESSING'
        )
        ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
        int tryReserve(
            @Param("key") UUID key,
            @Param("operationType") String operationType,
            @Param("walletId") Long walletId,
            @Param("toWalletId") Long toWalletId,
            @Param("amount") BigDecimal amount
        );
}
