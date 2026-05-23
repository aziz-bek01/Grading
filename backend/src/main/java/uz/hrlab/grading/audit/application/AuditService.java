package uz.hrlab.grading.audit.application;

/**
 * Append-only audit recorder (architecture §8.5, security-blueprint §9).
 *
 * <p>Implementations:
 * <ul>
 *   <li>MUST compute {@code hash_current = SHA-256(canonical(record) || hash_prev)}</li>
 *   <li>MUST NOT expose update/delete operations on the audit log</li>
 *   <li>MUST redact salary fields server-side before persisting JSON snapshots</li>
 * </ul>
 *
 * <p>The hash chain provides tamper detection — nightly verifier job is part
 * of the security-blueprint §9.2 deliverable.
 */
public interface AuditService {

    void record(AuditEvent event);
}
