package com.receiptvision.core.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Mapped to 409 Conflict (not 400) so clients can distinguish
 * "username taken" from generic validation errors.
 * Combined with rate-limiting this is the standard tradeoff:
 * GitHub etc. also return 409/422 here; full enumeration resistance
 * would require invite-only registration.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException(String message) {
        super(message);
    }
}
