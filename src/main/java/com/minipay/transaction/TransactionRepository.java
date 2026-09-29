package com.minipay.transaction;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
            SELECT t FROM Transaction t
            WHERE(t.fromWallet.id = :walletId OR t.toWallet.id = :walletId)
            AND(:type IS NULL OR t.type = :type)
            AND(:status IS NULL OR t.status = :status)
            AND(CAST(:from AS TIMESTAMP) IS NULL OR t.createdAt >= :from)
            AND(CAST(:to AS TIMESTAMP) IS NULL OR t.createdAt < :to)
            """)
    Page<Transaction> findHistory(Long walletId,
            TransactionType type, TransactionStatus status, Pageable pageable,
            LocalDateTime from, LocalDateTime to);
}
