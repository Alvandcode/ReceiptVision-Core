package com.receiptvision.core.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank @Size(min = 3, max = 32)
        @Pattern(regexp = "^[a-zA-Z0-9_-]{3,32}$")
        String username,

        @NotBlank @Size(min = 8, max = 100)
        String password) {
}
