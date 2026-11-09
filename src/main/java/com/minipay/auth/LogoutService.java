package com.minipay.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipay.session.RefreshTokenRepository;
import com.minipay.session.RefreshTokenService;
import com.minipay.session.Session;
import com.minipay.session.SessionRepository;

@Service 
public class LogoutService {
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;

    public LogoutService(RefreshTokenService refreshTokenService,
        RefreshTokenRepository refreshTokenRepository,
        SessionRepository sessionRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
        this.sessionRepository = sessionRepository;
    }

    @Transactional 
    public void logout(String rawToken) {
        if(rawToken == null || rawToken.isBlank()) return;

        String tokenHash = refreshTokenService.hashToken(rawToken);

        Optional<Long> sessionId = refreshTokenRepository.findSessionIdByTokenHash(tokenHash);

        if(sessionId.isEmpty()) return;

        Optional<Session> sessionResult = sessionRepository.findByIdForUpdate(sessionId.get());
        if(sessionResult.isEmpty()) return;

        Session session = sessionResult.get();

        if(session.getRevokedAt() != null) return;

        session.setRevokedAt(Instant.now());
        session.setRevokeReason("LOGOUT");
    }
}
