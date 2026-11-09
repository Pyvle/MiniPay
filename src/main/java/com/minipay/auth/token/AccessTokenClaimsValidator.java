package com.minipay.auth.token;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AccessTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private final Clock clock;

    public AccessTokenClaimsValidator() {
        this(Clock.systemUTC());
    }

    public AccessTokenClaimsValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            return invalidToken();
        }

        try {
            if (Long.parseLong(subject) <= 0) {
                return invalidToken();
            }
        } catch (NumberFormatException exception) {
            return invalidToken();
        }

        Object sessionId = jwt.getClaims().get("sid");
        if (!(sessionId instanceof Integer || sessionId instanceof Long)) {
            return invalidToken();
        }
        if (((Number) sessionId).longValue() <= 0) {
            return invalidToken();
        }

        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();

        if (issuedAt == null || expiresAt == null) {
            return invalidToken();
        }
        if (!expiresAt.isAfter(issuedAt)) {
            return invalidToken();
        }
        if (issuedAt.isAfter(clock.instant().plusSeconds(30))) {
            return invalidToken();
        }

        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                        "invalid_token",
                        "Invalid access token",
                        null));
    }
}
