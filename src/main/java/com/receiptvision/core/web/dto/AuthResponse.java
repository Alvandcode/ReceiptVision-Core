package com.receiptvision.core.web.dto;

public record AuthResponse(String username, String token, String tokenType) {

    public static AuthResponse bearer(String username, String token) {
        return new AuthResponse(username, token, "Bearer");
    }
}
