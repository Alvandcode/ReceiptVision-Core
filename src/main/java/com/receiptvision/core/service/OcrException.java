package com.receiptvision.core.service;

/**
 * Unchecked exception for OCR failures. Mapped to HTTP 422 by
 * {@code GlobalExceptionHandler}.
 */
public class OcrException extends RuntimeException {

    public OcrException(String message) {
        super(message);
    }

    public OcrException(String message, Throwable cause) {
        super(message, cause);
    }
}
