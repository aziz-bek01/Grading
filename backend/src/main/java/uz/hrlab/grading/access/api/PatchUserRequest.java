package uz.hrlab.grading.access.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PATCH /api/v1/users/{id}} — every field is optional
 * (null = "do not change"). Empty strings are rejected by the use case to
 * avoid accidentally blanking required columns.
 *
 * <p>Status transitions are restricted: a caller may move ACTIVE ↔ DISABLED
 * but cannot set INVITED (only the invite endpoint creates that state) and
 * cannot set LOCKED (that is system-level after failed-auth lockout).
 * Validation of the allowed set lives in {@code PatchUserUseCase}.
 */
public record PatchUserRequest(
        @Size(max = 255) String fullName,
        @Pattern(regexp = "ru-RU|uz-Cyrl-UZ|uz-Latn-UZ|en-US",
                 message = "locale must be one of ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US")
        String locale,
        @Pattern(regexp = "ACTIVE|DISABLED",
                 message = "status must be ACTIVE or DISABLED")
        String status
) {
}
