package com.minipay.session;

import java.time.Instant;

import com.minipay.user.User;
import com.minipay.user.UserState;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionAuthenticationService {
    private final SessionRepository sessionRepository;
    public SessionAuthenticationService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public User authenticate(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            throw invalidAuthentication();
        }

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(SessionAuthenticationService::invalidAuthentication);
        User user = session.getUser();

        if (!userId.equals(user.getId())
                || session.getRevokedAt() != null
                || !session.getExpiresAt().isAfter(Instant.now())
                || user.getState() != UserState.ACTIVE) {
            throw invalidAuthentication();
        }

        return user;
    }

    private static BadCredentialsException invalidAuthentication() {
        return new BadCredentialsException("Invalid authentication");
    }
}
