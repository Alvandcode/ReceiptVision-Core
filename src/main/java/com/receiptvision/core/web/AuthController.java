package com.receiptvision.core.web;

import com.receiptvision.core.service.AuthService;
import com.receiptvision.core.web.dto.AuthRequest;
import com.receiptvision.core.web.dto.AuthResponse;
import com.receiptvision.core.web.dto.RefreshRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Register, login, refresh and session info. Receipts are strictly per-user.")
public class AuthController {

    static final String ACCESS_COOKIE = "rv_at";
    static final String REFRESH_COOKIE = "rv_rt";

    private final AuthService authService;
    private final com.receiptvision.core.security.JwtService jwtService;
    private final com.receiptvision.core.security.TokenBlacklist tokenBlacklist;
    private final boolean cookieSecure;
    private final long refreshDays;

    public AuthController(AuthService authService,
            com.receiptvision.core.security.JwtService jwtService,
            com.receiptvision.core.security.TokenBlacklist tokenBlacklist,
            @Value("${app.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${app.auth.refresh-days:30}") long refreshDays) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
        this.cookieSecure = cookieSecure;
        this.refreshDays = refreshDays;
    }

    @PostMapping("/register")
    @Operation(summary = "Create a new account and receive tokens (JSON + HttpOnly cookies)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        AuthResponse tokens = authService.register(request);
        return withCookies(tokens, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive tokens (JSON + HttpOnly cookies)")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse tokens = authService.login(request);
        return withCookies(tokens, HttpStatus.OK);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate refresh token and get a new access token")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieRefresh,
            @RequestBody(required = false) RefreshRequest body) {
        String raw = cookieRefresh;
        if ((raw == null || raw.isBlank()) && body != null) {
            raw = body.refreshToken();
        }
        AuthResponse tokens = authService.refresh(raw);
        return withCookies(tokens, HttpStatus.OK);
    }

    @GetMapping("/me")
    @Operation(summary = "Current logged-in username (requires JWT)")
    public AuthResponse me(Authentication authentication) {
        if (authentication == null) {
            authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
        }
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.authentication.BadCredentialsException("Not authenticated");
        }
        // Token is not re-issued here; client keeps using its access token/cookies.
        return AuthResponse.empty(authentication.getName());
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke access (by jti) + refresh token and clear cookies")
    public ResponseEntity<Void> logout(jakarta.servlet.http.HttpServletRequest request,
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieRefresh,
            @RequestBody(required = false) RefreshRequest body) {
        // 1) Revoke access token from header OR access cookie.
        String access = bearerFrom(request);
        if (access == null) {
            access = cookie(request, ACCESS_COOKIE);
        }
        if (access != null && !access.isBlank()) {
            try {
                io.jsonwebtoken.Claims claims = jwtService.parseAndValidate(access);
                String jti = claims.getId();
                java.util.Date exp = claims.getExpiration();
                long expMillis = exp != null ? exp.getTime()
                        : System.currentTimeMillis() + jwtService.getExpirationMillis();
                tokenBlacklist.revoke(jti, expMillis);
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                // Invalid/expired token: nothing to revoke, still return 204.
            }
        }
        // 2) Revoke refresh token from cookie OR body.
        String refresh = cookieRefresh;
        if ((refresh == null || refresh.isBlank()) && body != null) {
            refresh = body.refreshToken();
        }
        authService.revokeRefresh(refresh);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    // ---- cookies ----

    private ResponseEntity<AuthResponse> withCookies(AuthResponse tokens, HttpStatus status) {
        ResponseCookie access = accessCookie(tokens.token());
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(REFRESH_COOKIE,
                        tokens.refreshToken() == null ? "" : tokens.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(refreshDays * 86400L)
                .sameSite("Strict");
        ResponseCookie refresh = builder.build();
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, access.toString())
                .header(HttpHeaders.SET_COOKIE, refresh.toString())
                .body(tokens);
    }

    private ResponseCookie accessCookie(String accessToken) {
        long maxAge = jwtService.getExpirationMillis() / 1000L;
        return ResponseCookie.from(ACCESS_COOKIE, accessToken == null ? "" : accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAge)
                .sameSite("Strict")
                .build();
    }

    private ResponseCookie clearAccessCookie() {
        return ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }

    private static String bearerFrom(jakarta.servlet.http.HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private static String cookie(jakarta.servlet.http.HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
