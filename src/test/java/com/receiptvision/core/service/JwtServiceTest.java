package com.receiptvision.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.receiptvision.core.security.JwtService;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService service() {
        return new JwtService("test-secret-0123456789-abcdef-0123456789", 1);
    }

    @Test
    void token_roundTripsUsername() {
        JwtService jwt = service();
        String token = jwt.generateToken("ali");
        assertThat(jwt.isValid(token)).isTrue();
        assertThat(jwt.extractUsername(token)).isEqualTo("ali");
    }

    @Test
    void tamperedToken_isInvalid() {
        JwtService jwt = service();
        String token = jwt.generateToken("ali") + "x";
        assertThat(jwt.isValid(token)).isFalse();
    }

    @Test
    void shortSecret_refused_failClosed() {
        assertThatThrownBy(() -> new JwtService("short", 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void placeholderSecret_refused_failClosed() {
        assertThatThrownBy(() -> new JwtService("change-me-in-production-min-32-chars-please", 12))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("", 12))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService("   ", 12))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiryBounds_enforced() {
        String secret = "test-secret-0123456789-abcdef-0123456789";
        assertThatThrownBy(() -> new JwtService(secret, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtService(secret, 100)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void token_hasJti_singleParse() {
        JwtService jwt = service();
        String token = jwt.generateToken("ali");
        assertThat(jwt.extractJti(token)).isNotBlank();
        assertThat(jwt.parseAndValidate(token).getSubject()).isEqualTo("ali");
    }
}
