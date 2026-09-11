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
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already taken");
        }
        AppUser saved = users.save(new AppUser(username, passwordEncoder.encode(request.password())));
        return AuthResponse.bearer(saved.getUsername(), jwtService.generateToken(saved.getUsername()));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(AuthRequest request) {
        // Throws BadCredentialsException on wrong password -> mapped to 401.
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                request.username().trim(), request.password()));
        return AuthResponse.bearer(request.username().trim(), jwtService.generateToken(request.username().trim()));
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
