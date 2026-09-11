package com.receiptvision.core.web.dto;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path);
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "timestamp", timestamp.toString(),
                "status", status,
                "error", error,
                "message", message == null ? "" : message,
                "path", path == null ? "" : path);
    }
}
