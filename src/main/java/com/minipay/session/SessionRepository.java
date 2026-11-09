package com.minipay.session;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SessionRepository extends JpaRepository<Session, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.id = :id")
    Optional<Session> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s FROM Session s
        WHERE s.user.id = :userId 
        AND s.revokedAt IS NULL
        ORDER BY s.id
        """)
    List<Session>  findUnrevokedByUserIdForUpdate(@Param("userId") Long userId);
}
