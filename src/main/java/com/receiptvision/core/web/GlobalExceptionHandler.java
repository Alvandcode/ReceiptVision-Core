package com.receiptvision.core.web;

import com.receiptvision.core.service.DuplicateUsernameException;
import com.receiptvision.core.service.OcrException;
import com.receiptvision.core.web.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            IllegalArgumentException ex, HttpServletRequest request) {
        // Sanitize: never leak absolute paths / temp names to clients.
        return build(HttpStatus.BAD_REQUEST, sanitize(ex.getMessage()), request);
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            DuplicateUsernameException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ErrorResponse> handleValidation(
            Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed: " + ex.getMessage(), request);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            EntityNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(OcrException.class)
    public ResponseEntity<ErrorResponse> handleOcr(
            OcrException ex, HttpServletRequest request) {
        log.warn("OCR failed for request {}: {}", request.getRequestURI(), ex.getMessage());
        // Generic message to clients: stderr may contain temp paths / versions.
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "OCR failed. Try a clearer image.", request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the configured maximum size", request);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            SecurityException ex, HttpServletRequest request) {
        // Never reveal whether the resource exists: same generic message.
        return build(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    @ExceptionHandler(org.springframework.security.core.userdetails.UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUnknownUser(
            org.springframework.security.core.userdetails.UsernameNotFoundException ex, HttpServletRequest request) {
        // Same message as bad credentials: no username oracle.
        return build(HttpStatus.UNAUTHORIZED, "Invalid username or password", request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleBadSort(
            RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid sort field", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "Bad request";
        }
        // Strip absolute Unix/Windows paths and temp-file names that may leak via
        // Tesseract stderr or file handling.
        String s = message.replaceAll("(?i)[a-z]:\\\\[^\\s\"']*", "[path]");
        s = s.replaceAll("/[\\w\\-./]*receipt-[^\\s\"']*", "[file]");
        s = s.replaceAll("/tmp/[^\\s\"']*", "[file]");
        s = s.replaceAll("(?i)C:\\\\[^\\s\"']*", "[path]");
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    private static ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(
                status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
