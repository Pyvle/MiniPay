package com.minipay.auth.token;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccessTokenClaimsValidatorTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private final AccessTokenClaimsValidator validator =
            new AccessTokenClaimsValidator(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void validClaimsShouldBeAccepted() {
        assertThat(validator.validate(jwt(claims())).hasErrors()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "sid", "iat", "exp"})
    void missingRequiredClaimShouldBeRejected(String name) {
        var claims = claims();
        claims.remove(name);
        assertThat(validator.validate(jwt(claims)).hasErrors()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-number", "0", "-1", "9223372036854775808"})
    void invalidSubjectShouldBeRejected(String subject) {
        var claims = claims();
        claims.put("sub", subject);
        assertThat(validator.validate(jwt(claims)).hasErrors()).isTrue();
    }

    @Test
    void stringFractionalAndNonPositiveSessionIdsShouldBeRejected() {
        for (Object sid : new Object[] {"2", 2.5d, 0L, -1L}) {
            var claims = claims();
            claims.put("sid", sid);
            assertThat(validator.validate(jwt(claims)).hasErrors()).as("sid=%s", sid).isTrue();
        }
    }

    @Test
    void integerAndLongSessionIdsShouldBeAccepted() {
        for (Object sid : new Object[] {2, 2L}) {
            var claims = claims();
            claims.put("sid", sid);
            assertThat(validator.validate(jwt(claims)).hasErrors()).isFalse();
        }
    }

    @Test
    void expirationBeforeOrEqualToIssueTimeShouldBeRejected() {
        for (Instant expiration : new Instant[] {NOW.minusSeconds(1), NOW}) {
            var claims = claims();
            claims.put("exp", expiration);
            assertThat(validator.validate(jwt(claims)).hasErrors()).isTrue();
        }
    }

    @Test
    void issueTimeBeyondThirtySecondToleranceShouldBeRejected() {
        var claims = claims();
        claims.put("iat", NOW.plusSeconds(31));
        assertThat(validator.validate(jwt(claims)).hasErrors()).isTrue();
    }

    @Test
    void issueTimeAtThirtySecondBoundaryShouldBeAccepted() {
        var claims = claims();
        claims.put("iat", NOW.plusSeconds(30));
        assertThat(validator.validate(jwt(claims)).hasErrors()).isFalse();
    }

    private Map<String, Object> claims() {
        return new HashMap<>(Map.of("sub", "1", "sid", 2L,
                "iat", NOW, "exp", NOW.plusSeconds(900)));
    }

    private Jwt jwt(Map<String, Object> claims) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn((String) claims.get("sub"));
        when(jwt.getClaims()).thenReturn(claims);
        when(jwt.getIssuedAt()).thenReturn((Instant) claims.get("iat"));
        when(jwt.getExpiresAt()).thenReturn((Instant) claims.get("exp"));
        return jwt;
    }
}
