package com.minipay.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.minipay.auth.dto.LoginResponse;
import com.minipay.auth.token.AccessTokenService;
import com.minipay.common.exception.EmailAlreadyExistsException;
import com.minipay.common.exception.InvalidCredentialsException;
import com.minipay.session.Session;
import com.minipay.session.SessionRepository;
import com.minipay.session.RefreshTokenService;
import com.minipay.session.RefreshTokenRepository;
import com.minipay.session.RefreshToken;
import com.minipay.user.User;
import com.minipay.user.UserRepository;
import com.minipay.user.UserState;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionRepository sessionRepository;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenService accessTokenService;
    private final Duration sessionTtl;
    private final Duration accessTokenTtl;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            SessionRepository sessionRepository,
            RefreshTokenService refreshTokenService,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenService accessTokenService,
            @Value("${app.auth.session-ttl}") Duration sessionTtl,
            @Value("${app.auth.access-token-ttl}") Duration accessTokenTtl) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenService = accessTokenService;
        this.sessionTtl = sessionTtl;
        this.accessTokenTtl = accessTokenTtl;
    }

    @Transactional
    public LoginResponse login(String email, String password) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailForUpdate(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())
                || user.getState() != UserState.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        Instant now = Instant.now();
        Instant sessionExpiresAt = now.plus(sessionTtl);
        Session session = new Session(user, now, sessionExpiresAt);
        sessionRepository.save(session);

        String token = refreshTokenService.generateToken();
        String tokenHash = refreshTokenService.hashToken(token);
        RefreshToken refreshToken = new RefreshToken(session, tokenHash, now, sessionExpiresAt);
        refreshTokenRepository.save(refreshToken);

        Instant accessExpiresAt = now.plus(accessTokenTtl);
        if (accessExpiresAt.isAfter(sessionExpiresAt)) {
            accessExpiresAt = sessionExpiresAt;
        }
        String accessToken = accessTokenService.generateToken(user.getId(), session.getId(), now, accessExpiresAt);
        return new LoginResponse(accessToken, token, "Bearer", 
            accessExpiresAt, sessionExpiresAt);
    }

    @Transactional
    public User register(String name, String email, String password) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = new User(name, normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        return userRepository.save(user);
    }
}
