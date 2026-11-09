package com.minipay.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipay.auth.dto.LoginResponse;
import com.minipay.auth.token.AccessTokenService;
import com.minipay.session.RefreshToken;
import com.minipay.session.RefreshTokenRepository;
import com.minipay.session.RefreshTokenService;
import com.minipay.session.Session;
import com.minipay.session.SessionRepository;
import com.minipay.user.UserState;

@Service
public class RefreshService {
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionRepository sessionRepository;
    private final AccessTokenService accessTokenService;
    private final Duration accessTokenTtl;

    public RefreshService(RefreshTokenService refreshTokenService,
            RefreshTokenRepository refreshTokenRepository,
            SessionRepository sessionRepository,
            AccessTokenService accessTokenService,
            @Value("${app.auth.access-token-ttl}") Duration accessTokenTtl) {
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionRepository = sessionRepository;
        this.accessTokenService = accessTokenService;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Transactional
    public Optional<LoginResponse> refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = refreshTokenService.hashToken(rawToken);

        Optional<Long> sessionId = refreshTokenRepository.findSessionIdByTokenHash(tokenHash);

        if (sessionId.isEmpty()) {
            return Optional.empty();
        }

        Optional<Session> sessionResult = sessionRepository.findByIdForUpdate(sessionId.get());

        if (sessionResult.isEmpty()) {
            return Optional.empty();
        }

        Session session = sessionResult.get();

        Optional<RefreshToken> tokenResult = refreshTokenRepository.findByTokenHash(tokenHash);

        if (tokenResult.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken oldToken = tokenResult.get();
        Instant now = Instant.now();

        if (session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(now)
                || session.getUser().getState() != UserState.ACTIVE) {
            return Optional.empty();
        }

        if (oldToken.getUsedAt() != null) {
            session.setRevokedAt(now);
            session.setRevokeReason("REFRESH_TOKEN_REUSE");
            return Optional.empty();
        }

        if (!oldToken.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }

        oldToken.setUsedAt(now);

        String newRawToken = refreshTokenService.generateToken();
        String newTokenHash = refreshTokenService.hashToken(newRawToken);

        RefreshToken newToken = new RefreshToken(session,
                newTokenHash,
                now,
                session.getExpiresAt());

        refreshTokenRepository.save(newToken);

        Instant accessExpiresAt = now.plus(accessTokenTtl);
        if (accessExpiresAt.isAfter(session.getExpiresAt())) {
            accessExpiresAt = session.getExpiresAt();
        }

        String accessToken = accessTokenService.generateToken(session.getUser().getId(),
                session.getId(), now, accessExpiresAt);
        return Optional.of(new LoginResponse(
                accessToken,
                newRawToken,
                "Bearer",
                accessExpiresAt,
                session.getExpiresAt()));
    }

}
