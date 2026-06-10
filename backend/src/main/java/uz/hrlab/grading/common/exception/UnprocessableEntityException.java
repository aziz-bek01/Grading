package uz.hrlab.grading.common.exception;

/**
 * 422 Unprocessable Entity — the request was syntactically valid (it parsed and
 * passed bean validation) but a domain/business rule rejects it.
 *
 * <p>Distinguished from {@link ValidationException} (400, a malformed/invalid
 * request the client should not have sent) and from {@link PermissionDeniedException}
 * (403, the caller lacks an authority). A 422 means "we understood you, but this
 * specific value cannot be processed under the current rules".
 *
 * <p>Introduced for the role→permission admin API (slice E2), where:
 * <ul>
 *   <li>{@code PERMISSION_RESTRICTED} — a requested code is in
 *       {@link uz.hrlab.grading.access.application.RestrictedPermissions} and may
 *       never be granted to any role;</li>
 *   <li>{@code PERMISSION_NOT_HELD_BY_CALLER} — a requested code the caller does
 *       not themselves hold, which would be a privilege escalation.</li>
 * </ul>
 *
 * <p>The {@code code} is carried so the frontend can switch on it; the message is
 * sanitized (no tenant/PII/salary data).
 */
public class UnprocessableEntityException extends BaseDomainException {

    public UnprocessableEntityException(String code, String safeMessage) {
        super(code, safeMessage);
    }
}
