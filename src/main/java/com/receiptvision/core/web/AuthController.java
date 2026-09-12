package com.receiptvision.core.web;

import com.receiptvision.core.service.AuthService;
import com.receiptvision.core.web.dto.AuthRequest;
import com.receiptvision.core.web.dto.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Register, login, and session info. Receipts are strictly per-user.")
public class AuthController {

    private final AuthService authService;
    private final com.receiptvision.core.security.JwtService jwtService;
    private final com.receiptvision.core.security.TokenBlacklist tokenBlacklist;

    public AuthController(AuthService authService,
            com.receiptvision.core.security.JwtService jwtService,
            com.receiptvision.core.security.TokenBlacklist tokenBlacklist) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new account and receive a JWT")
    public AuthResponse register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive a JWT")
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request);
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
        // Token is not re-issued here; client keeps using its stored JWT.
        return new AuthResponse(authentication.getName(), "", "Bearer");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke the current JWT (server-side denylist by jti)")
    public void logout(jakarta.servlet.http.HttpServletRequest request) {
        String header = request.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            if (!token.isEmpty()) {
                try {
                    io.jsonwebtoken.Claims claims = jwtService.parseAndValidate(token);
                    String jti = claims.getId();
                    java.util.Date exp = claims.getExpiration();
                    long expMillis = exp != null ? exp.getTime()
                            : System.currentTimeMillis() + jwtService.getExpirationMillis();
                    tokenBlacklist.revoke(jti, expMillis);
                } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                    // Invalid/expired token: nothing to revoke, still return 204.
                }
            }
        }
    }
}
