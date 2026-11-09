package com.minipay.auth.token;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import com.minipay.config.JwtConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenDecoderTest {
    // Test-only 512-bit key, also long enough to construct a valid HS512 negative case.
    private static final byte[] KEY =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    .getBytes(StandardCharsets.US_ASCII);
    private JwtDecoder decoder;
    private AccessTokenService service;
    private Instant now;

    @BeforeEach
    void configureRealEncoderAndDecoder() {
        JwtConfig config = new JwtConfig();
        SecretKey key = config.jwtSecretKey(Base64.getEncoder().encodeToString(KEY));
        decoder = config.jwtDecoder(key);
        service = new AccessTokenService(config.jwtEncoder(key));
        now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    @Test
    void issuedTokenShouldBeAcceptedAndIdentifyUserAndSession() {
        var jwt = decoder.decode(service.generateToken(12L, 34L, now, now.plusSeconds(900)));
        assertThat(jwt.getSubject()).isEqualTo("12");
        assertThat(((Number) jwt.getClaim("sid")).longValue()).isEqualTo(34L);
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plusSeconds(900));
    }

    @Test
    void signatureFromAnotherKeyShouldBeRejected() throws Exception {
        byte[] otherKey = KEY.clone();
        otherKey[0] ^= 1;
        rejected(sign(claims(), JWSAlgorithm.HS256, otherKey));
    }

    @Test
    void modifiedSignedPayloadShouldBeRejected() throws Exception {
        String token = sign(claims(), JWSAlgorithm.HS256, KEY);
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String modified = payload.replace("\"sub\":\"12\"", "\"sub\":\"99\"");
        assertThat(modified).isNotEqualTo(payload);
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(modified.getBytes(StandardCharsets.UTF_8));
        rejected(String.join(".", parts));
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-jwt", "a.b.c", "eyJhbGciOiJIUzI1NiJ9.bad.bad"})
    void malformedTokenShouldBeRejected(String token) {
        rejected(token);
    }

    @Test
    void unsignedTokenShouldBeRejected() {
        rejected(new PlainJWT(claimSet(claims())).serialize());
    }

    @Test
    void validSignatureUsingUnapprovedAlgorithmShouldBeRejected() throws Exception {
        rejected(sign(claims(), JWSAlgorithm.HS512, KEY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "sid", "iat", "exp"})
    void missingRequiredClaimShouldBeRejected(String claim) throws Exception {
        var claims = claims();
        claims.remove(claim);
        rejected(sign(claims, JWSAlgorithm.HS256, KEY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "0", "-1", "9223372036854775808"})
    void invalidSubjectShouldBeRejected(String subject) throws Exception {
        var claims = claims();
        claims.put("sub", subject);
        rejected(sign(claims, JWSAlgorithm.HS256, KEY));
    }

    @Test
    void invalidSessionIdTypesAndValuesShouldBeRejected() throws Exception {
        for (Object sid : new Object[] {"34", 34.5d, 0L, -1L}) {
            var claims = claims();
            claims.put("sid", sid);
            rejected(sign(claims, JWSAlgorithm.HS256, KEY));
        }
    }

    @Test
    void expirationBeforeIssueTimeShouldBeRejected() throws Exception {
        var claims = claims();
        claims.put("exp", Date.from(now.minusSeconds(1)));
        rejected(sign(claims, JWSAlgorithm.HS256, KEY));
    }

    @Test
    void tokenExpiredBeyondToleranceShouldBeRejected() throws Exception {
        var claims = claims();
        claims.put("iat", Date.from(now.minusSeconds(900)));
        claims.put("exp", Date.from(now.minusSeconds(120)));
        rejected(sign(claims, JWSAlgorithm.HS256, KEY));
    }

    @Test
    void issueTimeBeyondToleranceShouldBeRejected() throws Exception {
        var claims = claims();
        claims.put("iat", Date.from(now.plusSeconds(120)));
        rejected(sign(claims, JWSAlgorithm.HS256, KEY));
    }

    @Test
    void notBeforeBeyondToleranceShouldBeRejected() throws Exception {
        var claims = claims();
        claims.put("nbf", Date.from(now.plusSeconds(120)));
        rejected(sign(claims, JWSAlgorithm.HS256, KEY));
    }

    private Map<String, Object> claims() {
        return new HashMap<>(Map.of("sub", "12", "sid", 34L,
                "iat", Date.from(now), "exp", Date.from(now.plusSeconds(900))));
    }

    private JWTClaimsSet claimSet(Map<String, Object> claims) {
        var builder = new JWTClaimsSet.Builder();
        claims.forEach(builder::claim);
        return builder.build();
    }

    private String sign(Map<String, Object> claims, JWSAlgorithm algorithm, byte[] key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claimSet(claims));
        jwt.sign(new MACSigner(key));
        return jwt.serialize();
    }

    private void rejected(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }
}
