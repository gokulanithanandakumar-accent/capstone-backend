package com.insuranceapp.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.insuranceapp.backend.config.JwtConfig;
import com.insuranceapp.backend.config.JwtProperties;

class JwtTokenServiceTest {
    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";
    private final JwtConfig jwtConfig = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(SECRET, "healthdekho", Duration.ofHours(1));
    private final JwtDecoder decoder = jwtConfig.jwtDecoder(properties);
    private final CustomerPrincipal principal =
            new CustomerPrincipal(1001L, "gokul@gmail.com", "{bcrypt}hash", "Gokul", true);

    @Test
    void issuesSignedTokenWithCustomerSubjectAndExpiry() {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        JwtTokenService service = new JwtTokenService(jwtConfig.jwtEncoder(properties), properties,
                Clock.fixed(now, ZoneOffset.UTC));

        JwtTokenService.IssuedToken token = service.issue(principal);
        Jwt jwt = decoder.decode(token.value());

        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofHours(1)));
        assertThat(jwt.getSubject()).isEqualTo("1001");
        assertThat(jwt.getClaimAsString(JwtTokenService.EMAIL_CLAIM)).isEqualTo("gokul@gmail.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("healthdekho");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(token.expiresAt());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void decoderRejectsExpiredToken() {
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        JwtTokenService service = new JwtTokenService(jwtConfig.jwtEncoder(properties), properties, twoHoursAgo);

        String token = service.issue(principal).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsTokenSignedWithDifferentSecret() {
        JwtProperties other = new JwtProperties("another-secret-that-is-also-32-bytes-long!!", "healthdekho",
                Duration.ofHours(1));
        String token = new JwtTokenService(jwtConfig.jwtEncoder(other), other, Clock.systemUTC()).issue(principal).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsTokenFromDifferentIssuer() {
        JwtProperties otherIssuer = new JwtProperties(SECRET, "someone-else", Duration.ofHours(1));
        String token = new JwtTokenService(jwtConfig.jwtEncoder(otherIssuer), otherIssuer, Clock.systemUTC())
                .issue(principal).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void configRejectsSecretShorterThan32Bytes() {
        JwtProperties weak = new JwtProperties("too-short", "healthdekho", Duration.ofHours(1));

        assertThatThrownBy(() -> jwtConfig.jwtEncoder(weak)).isInstanceOf(IllegalStateException.class);
    }
}
