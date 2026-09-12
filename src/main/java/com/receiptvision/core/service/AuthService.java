package com.receiptvision.core.service;

import com.receiptvision.core.domain.AppUser;
import com.receiptvision.core.repository.UserRepository;
import com.receiptvision.core.security.JwtService;
import com.receiptvision.core.web.dto.AuthRequest;
import com.receiptvision.core.web.dto.AuthResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String username = normalize(request.username());
        validatePassword(request.password());
        if (users.existsByUsernameIgnoreCase(username) || users.existsByUsername(username)) {
            throw new DuplicateUsernameException("Username already taken");
        }
        AppUser saved = users.save(new AppUser(username, passwordEncoder.encode(request.password())));
        return AuthResponse.bearer(saved.getUsername(), jwtService.generateToken(saved.getUsername()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        String username = normalize(request.username());
        // Throws BadCredentialsException on wrong password -> mapped to 401.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                username, request.password()));
        return AuthResponse.bearer(username, jwtService.generateToken(username));
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
