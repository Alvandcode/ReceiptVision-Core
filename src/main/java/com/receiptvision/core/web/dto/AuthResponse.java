package com.receiptvision.core.web.dto;

public record AuthResponse(String username, String token, String refreshToken, String tokenType) {

    public static AuthResponse bearer(String username, String token) {
        return new AuthResponse(username, token, null, "Bearer");
    }

    public static AuthResponse bearer(String username, String accessToken, String refreshToken) {
        return new AuthResponse(username, accessToken, refreshToken, "Bearer");
    }

    public static AuthResponse empty(String username) {
        return new AuthResponse(username, "", null, "Bearer");
    }
}
