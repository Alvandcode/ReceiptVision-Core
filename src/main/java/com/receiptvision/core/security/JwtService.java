package com.receiptvision.core.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    static final String INSECURE_PLACEHOLDER = "change-me-in-production-min-32-chars-please";

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration-hours:2}") long expirationHours) {
        if (secret == null || secret.isBlank()
                || INSECURE_PLACEHOLDER.equals(secret.trim())
                || secret.toLowerCase().contains("change-me")) {
            throw new IllegalStateException(
                    "app.jwt.secret is missing or insecure. Set APP_JWT_SECRET env to a random 32+ byte value "
                            + "(e.g. openssl rand -base64 48). Refusing to start with default/empty secret.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes. Set APP_JWT_SECRET env in production.");
        }
        if (expirationHours <= 0 || expirationHours > 24) {
            throw new IllegalStateException(
                    "app.jwt.expiration-hours must be between 1 and 24 (was " + expirationHours + "). "
                            + "Use refresh tokens for long sessions.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationHours * 3_600_000L;
    }

    public String generateToken(String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    /**
     * Single-parse entry point: verifies signature + expiry and returns claims.
     * Use this instead of calling isValid() + extractUsername() separately
     * (which parses twice).
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parseAndValidate(token).getSubject();
    }

    public String extractJti(String token) {
        return parseAndValidate(token).getId();
    }

    public Date extractExpiration(String token) {
        return parseAndValidate(token).getExpiration();
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }

    public boolean isValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }
}
