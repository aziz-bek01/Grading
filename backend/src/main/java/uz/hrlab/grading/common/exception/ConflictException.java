package uz.hrlab.grading.common.exception;

/**
 * 409 Conflict — the request is well-formed and authorized but conflicts with
 * the current state of the resource (a uniqueness clash or an in-use guard).
 *
 * <p>Distinguished from {@link UnprocessableEntityException} (422, a value that
 * cannot be processed under the business rules) and from
 * {@link ValidationException} (400, a malformed request). A 409 means "what you
 * asked for can't happen because something already exists / is still in use".
 *
 * <p>Introduced for the custom-role admin API (slice E3):
 * <ul>
 *   <li>{@code ROLE_CODE_RESERVED} — the requested custom-role code collides with
 *       a SYSTEM role code (case-insensitive); the system namespace is reserved;</li>
 *   <li>{@code ROLE_CODE_EXISTS} — a custom role with that code already exists in
 *       the tenant;</li>
 *   <li>{@code ROLE_IN_USE} — the custom role is still assigned to at least one
 *       user, so it may not be deleted (revoke the assignments first to protect
 *       users from a silent privilege loss).</li>
 * </ul>
 *
 * <p>The {@code code} is carried so the frontend can switch on it; the message is
 * sanitized (no tenant/PII/salary data).
 */
public class ConflictException extends BaseDomainException {

    public ConflictException(String code, String safeMessage) {
        super(code, safeMessage);
    }
}
