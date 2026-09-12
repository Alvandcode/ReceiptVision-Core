package com.receiptvision.core.service;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.domain.RefreshToken;
import com.receiptvision.core.repository.RefreshTokenRepository;
import com.receiptvision.core.repository.UserRepository;
import com.receiptvision.core.security.JwtService;
import com.receiptvision.core.web.dto.AuthRequest;
import com.receiptvision.core.web.dto.AuthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final long refreshDays;
    private final java.security.SecureRandom secureRandom = new java.security.SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService,
            @Value("${app.auth.refresh-days:30}") long refreshDays) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        if (refreshDays < 1 || refreshDays > 90) {
            throw new IllegalStateException("app.auth.refresh-days must be 1-90");
        }
        this.refreshDays = refreshDays;
    }

    // Test-friendly constructor (default 30d refresh).
    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService) {
        this(users, null, passwordEncoder, authenticationManager, jwtService, 30L);
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String username = normalize(request.username());
        validatePassword(request.password());
        if (users.existsByUsernameIgnoreCase(username) || users.existsByUsername(username)) {
            throw new DuplicateUsernameException("Username already taken");
        }
        AppUser saved = users.save(new AppUser(username, passwordEncoder.encode(request.password())));
        return issueTokens(saved);
    }

    @Transactional
    public AuthResponse login(AuthRequest request) {
        String username = normalize(request.username());
        // Throws BadCredentialsException on wrong password -> mapped to 401.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                username, request.password()));
        AppUser user = requireUser(username);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Not authenticated");
        }
        String hash = sha256Hex(rawRefreshToken.trim());
        RefreshToken row = refreshTokens.findByTokenHash(hash).orElseThrow(
                () -> new BadCredentialsException("Not authenticated"));
        if (row.isRevoked()) {
            // Possible theft/reuse: revoke all sessions for this owner.
            try {
                refreshTokens.revokeAllForOwner(row.getOwner());
            } catch (Exception ignored) {
                // best-effort
            }
            throw new BadCredentialsException("Not authenticated");
        }
        if (row.getExpiresAt().isBefore(java.time.Instant.now())) {
            row.setRevoked(true);
            throw new BadCredentialsException("Not authenticated");
        }
        // Rotate: revoke old, issue new pair.
        row.setRevoked(true);
        AppUser owner = row.getOwner();
        // Re-attach owner if lazy proxy detached (within transaction it's managed).
        return issueTokens(owner);
    }

    @Transactional
    public void revokeRefresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank() || refreshTokens == null) {
            return;
        }
        String hash = sha256Hex(rawRefreshToken.trim());
        refreshTokens.findByTokenHash(hash).ifPresent(r -> r.setRevoked(true));
    }

    @Transactional
    public void revokeAllForUser(String username) {
        AppUser user = requireUser(username);
        refreshTokens.revokeAllForOwner(user);
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(String username) {
        if (username == null) {
            throw new IllegalArgumentException("User not found");
        }
        String normalized = normalize(username);
        return users.findByUsernameIgnoreCase(normalized)
                .or(() -> users.findByUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private AuthResponse issueTokens(AppUser owner) {
        String access = jwtService.generateToken(owner.getUsername());
        // Tests use the 4-arg constructor with null repository: access-only mode.
        if (refreshTokens == null) {
            return AuthResponse.bearer(owner.getUsername(), access);
        }
        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String refreshRaw = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RefreshToken row = new RefreshToken(owner, sha256Hex(refreshRaw),
                java.time.Instant.now().plus(refreshDays, java.time.temporal.ChronoUnit.DAYS));
        refreshTokens.save(row);
        return AuthResponse.bearer(owner.getUsername(), access, refreshRaw);
    }

    static String sha256Hex(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Daily purge of expired refresh rows + revoked rows older than 7 days. */
    @Scheduled(fixedDelayString = "${app.purge-interval-ms:3600000}")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        if (refreshTokens == null) {
            return;
        }
        try {
            refreshTokens.deleteExpired(java.time.Instant.now(),
                    java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS));
        } catch (Exception ignored) {
            // best-effort; next run retries
        }
    }

    static String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 100) {
            throw new IllegalArgumentException("Password must be 8-100 characters");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("Password must contain at least one letter and one digit");
        }
    }
}
