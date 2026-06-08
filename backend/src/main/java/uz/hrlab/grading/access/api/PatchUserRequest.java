package uz.hrlab.grading.access.api;

import jakarta.validation.constraints.Email;
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
 *
 * <h3>Credential edits (decision doc 08 extension)</h3>
 * <ul>
 *   <li>{@code email} — the principal RESOLVE KEY (the JWT email claim is matched
 *       against {@code public.users.email}). A change is pushed to the IdP
 *       all-or-nothing in {@link
 *       uz.hrlab.grading.access.application.PatchUserUseCase}. The {@code @Email}
 *       bound is a cheap syntactic guard; uniqueness is enforced in the use
 *       case.</li>
 *   <li>{@code password} — admin-set IdP credential reset. OPTIONAL at the bean
 *       layer because the full ZITADEL complexity rule depends on the runtime
 *       IdP flag and is enforced in the use case (mirrors the invite flow). The
 *       {@code @Size} cap is a safety bound; the password is never logged and
 *       never echoed back in the response.</li>
 * </ul>
 */
public record PatchUserRequest(
        @Size(max = 255) String fullName,
        @Pattern(regexp = "ru-RU|uz-Cyrl-UZ|uz-Latn-UZ|en-US",
                 message = "locale must be one of ru-RU, uz-Cyrl-UZ, uz-Latn-UZ, en-US")
        String locale,
        @Pattern(regexp = "ACTIVE|DISABLED",
                 message = "status must be ACTIVE or DISABLED")
        String status,
        @Email @Size(max = 320) String email,
        @Size(max = 200) String password
) {
}
