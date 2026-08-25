package com.mingo.backend.common.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String message,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(Instant.now(), status, message, null);
    }

    public static ErrorResponse of(int status, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, message, fieldErrors);
    }
}
