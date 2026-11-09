package com.minipay.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("select w.id from Wallet w where w.user.id = :userId")
    Optional<Long> findIdByUserId(@Param("userId") Long userId);

    @Query("select w.user.state from Wallet w where w.id = :walletId")
    Optional<com.minipay.user.UserState> findOwnerState(@Param("walletId") Long walletId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);
}
