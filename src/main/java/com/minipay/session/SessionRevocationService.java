package com.minipay.session;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service 
public class SessionRevocationService {
    private final SessionRepository sessionRepository;

    public SessionRevocationService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public void revokeAllForUser(Long userId, String reason) {
        List<Session> sessions = sessionRepository.findUnrevokedByUserIdForUpdate(userId);
        Instant instant = Instant.now();

        for(Session session : sessions) {
            session.setRevokedAt(instant);
            session.setRevokeReason(reason);
        }
    }

}
