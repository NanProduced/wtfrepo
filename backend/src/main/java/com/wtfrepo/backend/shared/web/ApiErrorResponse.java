package com.wtfrepo.backend.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldViolation> violations) {

    public static ApiErrorResponse of(
            Instant timestamp,
            int status,
            String error,
            ErrorCode code,
            String message,
            String path,
            String requestId,
            List<FieldViolation> violations) {
        return new ApiErrorResponse(
                timestamp, status, error, code.name(), message, path, requestId, violations);
    }

    public record FieldViolation(String field, String message) {}
}
