package uz.hrlab.grading.audit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable carrier of the exact fields that feed one audit row's SHA-256
 * {@code hash_current} (security-blueprint §9.2). It exists so the SINGLE
 * canonical hashing implementation in {@link AuditHashCalculator} is fed by
 * BOTH the writer ({@code JpaAuditService}) and the reader
 * ({@code AuditChainVerifier}) through the same typed shape — the writer and the
 * verifier can never drift because there is exactly one {@code compute(...)}
 * entry point and one field order.
 *
 * <p>{@code beforeJson}/{@code afterJson} are the RAW column strings (the
 * writer's freshly-serialized JSON, or the JSONB text read back from the DB).
 * {@link AuditHashCalculator} re-canonicalizes them so a JSONB round-trip
 * (whitespace / key-order / numeric-format normalization) does not change the
 * hash.
 *
 * <p>{@code hashFormatVersion} (L1 defence-in-depth) is bound INTO the hash for
 * current-format rows so a silent version downgrade cannot be used to smuggle a
 * payload tamper past the verifier.
 */
public record AuditHashInput(
        UUID id,
        UUID tenantId,
        UUID projectId,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        String beforeJson,
        String afterJson,
        OffsetDateTime createdAt,
        short hashFormatVersion,
        String prevHash
) {
}
