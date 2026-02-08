package com.wtfrepo.backend.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldViolation> violations =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(this::toFieldViolation)
                        .toList();

        ApiErrorResponse body =
                createBody(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.VALIDATION_ERROR,
                        "Validation failed",
                        request,
                        violations);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException ex, HttpServletRequest request) {
        ApiErrorResponse body =
                createBody(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), request, null);
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuth(
            AuthenticationException ex, HttpServletRequest request) {
        ApiErrorResponse body =
                createBody(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.UNAUTHORIZED,
                        "Authentication required",
                        request,
                        null);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(
            AccessDeniedException ex, HttpServletRequest request) {
        ApiErrorResponse body =
                createBody(
                        HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Access denied", request, null);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {
        log.error(
                "unhandled_exception requestId={} uri={}",
                requestId(request),
                request.getRequestURI(),
                ex);
        ApiErrorResponse body =
                createBody(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_ERROR,
                        "Internal server error",
                        request,
                        null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ApiErrorResponse createBody(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldViolation> violations) {
        return ApiErrorResponse.of(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode,
                message,
                request.getRequestURI(),
                requestId(request),
                violations);
    }

    private String requestId(HttpServletRequest request) {
        Object attr = request.getAttribute(RequestIdConstants.ATTRIBUTE_NAME);
        return attr != null ? String.valueOf(attr) : null;
    }

    private ApiErrorResponse.FieldViolation toFieldViolation(FieldError fieldError) {
        return new ApiErrorResponse.FieldViolation(
                fieldError.getField(), fieldError.getDefaultMessage());
    }
}
