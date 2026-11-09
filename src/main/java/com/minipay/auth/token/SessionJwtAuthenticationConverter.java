package com.minipay.auth.token;

import java.util.List;

import com.minipay.session.SessionAuthenticationService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class SessionJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final SessionAuthenticationService sessionAuthenticationService;

    public SessionJwtAuthenticationConverter(SessionAuthenticationService sessionAuthenticationService) {
        this.sessionAuthenticationService = sessionAuthenticationService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        Long sessionId = ((Number) jwt.getClaims().get("sid")).longValue();
        sessionAuthenticationService.authenticate(userId, sessionId);
        return new JwtAuthenticationToken(jwt, List.of(), userId.toString());
    }
}
