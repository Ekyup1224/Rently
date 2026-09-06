package mn.innex.stay.common.web;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every endpoint returns, so both frontends need one
 * error handler. {@code code} is stable and safe to branch on; {@code message}
 * is for developers, not end users (the UI localizes off {@code code}).
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        Instant timestamp) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, Instant.now());
    }

    public static ErrorResponse of(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, fieldErrors, Instant.now());
    }
}
