package com.receiptvision.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.repository.UserRepository;
import com.receiptvision.core.security.JwtService;
import com.receiptvision.core.web.dto.AuthRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Mock
    private AuthenticationManager authManager;
    @Mock
    private JwtService jwtService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, authManager, jwtService);
    }

    @Test
    void register_normalizesUsernameToLowercase() {
        when(users.existsByUsernameIgnoreCase("ali")).thenReturn(false);
        when(users.existsByUsername("ali")).thenReturn(false);
        when(encoder.encode("Pass1234")).thenReturn("hash");
        when(users.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken("ali")).thenReturn("tok");

        var res = service.register(new AuthRequest("ALI", "Pass1234"));
        assertThat(res.username()).isEqualTo("ali");
        assertThat(res.token()).isEqualTo("tok");
    }

    @Test
    void register_duplicateMapsTo409() {
        when(users.existsByUsernameIgnoreCase("ali")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new AuthRequest("ali", "Pass1234")))
                .isInstanceOf(DuplicateUsernameException.class);
    }

    @Test
    void register_rejectsWeakPassword() {
        assertThatThrownBy(() -> service.register(new AuthRequest("ali", "password")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("letter and one digit");
        assertThatThrownBy(() -> service.register(new AuthRequest("ali", "12345678")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireUser_isCaseInsensitive() {
        AppUser ali = new AppUser("ali", "hash");
        when(users.findByUsernameIgnoreCase("ali")).thenReturn(Optional.of(ali));
        assertThat(service.requireUser("ALI")).isSameAs(ali);
    }

    @Test
    void register_issuesRefreshToken_whenRepositoryPresent() {
        com.receiptvision.core.repository.RefreshTokenRepository rt =
                org.mockito.Mockito.mock(com.receiptvision.core.repository.RefreshTokenRepository.class);
        AuthService svc = new AuthService(users, rt, encoder, authManager, jwtService, 30L);
        when(users.existsByUsernameIgnoreCase("ali")).thenReturn(false);
        when(users.existsByUsername("ali")).thenReturn(false);
        when(encoder.encode("Pass1234")).thenReturn("hash");
        when(users.save(any(AppUser.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generateToken("ali")).thenReturn("access-tok");

        var res = svc.register(new AuthRequest("ali", "Pass1234"));
        assertThat(res.token()).isEqualTo("access-tok");
        assertThat(res.refreshToken()).isNotBlank();
        org.mockito.Mockito.verify(rt).save(any(com.receiptvision.core.domain.RefreshToken.class));
    }

    @Test
    void refresh_rotatesTokens() {
        com.receiptvision.core.repository.RefreshTokenRepository rt =
                org.mockito.Mockito.mock(com.receiptvision.core.repository.RefreshTokenRepository.class);
        AuthService svc = new AuthService(users, rt, encoder, authManager, jwtService, 30L);
        AppUser ali = new AppUser("ali", "hash");
        String raw = "refresh-raw-token-value-1234567890";
        String hash = AuthService.sha256Hex(raw);
        com.receiptvision.core.domain.RefreshToken row =
                new com.receiptvision.core.domain.RefreshToken(ali, hash,
                        java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        when(rt.findByTokenHash(hash)).thenReturn(Optional.of(row));
        when(jwtService.generateToken("ali")).thenReturn("new-access");

        var res = svc.refresh(raw);
        assertThat(res.token()).isEqualTo("new-access");
        assertThat(res.refreshToken()).isNotBlank().isNotEqualTo(raw);
        assertThat(row.isRevoked()).isTrue();
    }

    @Test
    void refresh_rejectsUnknownToken() {
        com.receiptvision.core.repository.RefreshTokenRepository rt =
                org.mockito.Mockito.mock(com.receiptvision.core.repository.RefreshTokenRepository.class);
        AuthService svc = new AuthService(users, rt, encoder, authManager, jwtService, 30L);
        when(rt.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> svc.refresh("nope"))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test
    void refresh_reuseRevoked_revokesAll() {
        com.receiptvision.core.repository.RefreshTokenRepository rt =
                org.mockito.Mockito.mock(com.receiptvision.core.repository.RefreshTokenRepository.class);
        AuthService svc = new AuthService(users, rt, encoder, authManager, jwtService, 30L);
        AppUser ali = new AppUser("ali", "hash");
        String raw = "reused-token";
        String hash = AuthService.sha256Hex(raw);
        com.receiptvision.core.domain.RefreshToken row =
                new com.receiptvision.core.domain.RefreshToken(ali, hash,
                        java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS));
        row.setRevoked(true);
        when(rt.findByTokenHash(hash)).thenReturn(Optional.of(row));
        assertThatThrownBy(() -> svc.refresh(raw))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        org.mockito.Mockito.verify(rt).revokeAllForOwner(ali);
    }
}
