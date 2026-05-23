package uz.hrlab.grading.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Canonical error envelope contract (design-foundation §21.6).
 *
 * <pre>
 * { "code": "PERMISSION_DENIED", "message": "Action not permitted",
 *   "correlation_id": "uuid" }
 * </pre>
 *
 * <p>Extras ({@code timestamp}, {@code traceId}, {@code fieldErrors}) are
 * optional and only populated when relevant. Stack traces never appear.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        String traceId,
        OffsetDateTime timestamp,
        List<FieldError> fieldErrors
) {

    public static ErrorResponse of(String code, String message, String correlationId, String traceId) {
        return new ErrorResponse(code, message, correlationId, traceId, OffsetDateTime.now(), null);
    }

    public static ErrorResponse withFieldErrors(String code, String message, String correlationId,
                                                String traceId, List<FieldError> fieldErrors) {
        return new ErrorResponse(code, message, correlationId, traceId, OffsetDateTime.now(), fieldErrors);
    }

    public record FieldError(String field, String code, String message) {}
}
