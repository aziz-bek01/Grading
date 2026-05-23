package uz.hrlab.grading.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Standard success envelope. The error envelope is {@link ErrorResponse}.
 *
 * <p>Aligned with design-foundation §21.6: success responses surface payload
 * data plus a correlation id; never expose stack traces.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, String correlationId, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> of(T data, String correlationId) {
        return new ApiResponse<>(data, correlationId, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, null, OffsetDateTime.now());
    }
}
