package com.minipay.session;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("""
        select t.session.id
        from RefreshToken t
        where t.tokenHash = :tokenHash
    """)
    Optional<Long> findSessionIdByTokenHash(@Param("tokenHash") String tokenHash);
}
