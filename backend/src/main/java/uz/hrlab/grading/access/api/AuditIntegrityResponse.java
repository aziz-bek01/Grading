package uz.hrlab.grading.access.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MVP1-E10-1 — response of {@code GET /api/v1/audit/integrity}. Lets the
 * frontend "Verify" action turn the audit badge from "Not independently
 * verified" into an evidence-backed state.
 *
 * <p><b>Honest reporting across the hash-format boundary.</b> Append-only
 * linkage is checked for every row; content is independently re-hashed only for
 * rows written in the current canonical format. The counts let the frontend say
 * exactly: "Linkage intact across {@code checked_count} rows;
 * {@code independently_verified_count} independently hash-verified (since
 * {@code verifiable_from}); {@code legacy_unverifiable_count} legacy rows whose
 * content hash is not independently verifiable." Legacy rows are NEVER a break.
 *
 * <p>Serializes snake_case via the global Jackson strategy, e.g. INTACT with a
 * mix of legacy + current rows:
 * <pre>
 * {
 *   "tenant_id": "6b1e...",
 *   "status": "INTACT",
 *   "intact": true,
 *   "checked_count": 1284,
 *   "chain_length": 1284,
 *   "independently_verified_count": 1200,
 *   "legacy_unverifiable_count": 84,
 *   "verifiable_from": "2026-07-15T00:00:00.000000Z",
 *   "verified_through": "2026-07-15T09:41:12.512874Z",
 *   "bounded": false,
 *   "max_rows": 50000,
 *   "verified_at": "2026-07-15T09:42:03.114Z"
 * }
 * </pre>
 * A BROKEN result additionally carries {@code first_break}:
 * <pre>
 * {
 *   "status": "BROKEN",
 *   "intact": false,
 *   "first_break": {
 *     "row_id": "9f2c...",
 *     "created_at": "2026-07-14T18:03:00.000000Z",
 *     "break_type": "HASH_MISMATCH",
 *     "expected_hash": "a1b2...",
 *     "actual_hash": "deadbeef..."
 *   }
 * }
 * </pre>
 * {@code break_type} is one of {@code HASH_MISMATCH}, {@code BROKEN_PREV_LINK},
 * {@code GAP}, {@code VERSION_REGRESSION} (a v2→v1 downgrade-to-hide tamper).
 * Null fields ({@code from}, {@code to}, {@code verifiable_from},
 * {@code verified_through}, {@code first_break}) are omitted.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditIntegrityResponse(
        UUID tenantId,
        String status,
        boolean intact,
        long checkedCount,
        long chainLength,
        long independentlyVerifiedCount,
        long legacyUnverifiableCount,
        OffsetDateTime verifiableFrom,
        OffsetDateTime verifiedThrough,
        OffsetDateTime from,
        OffsetDateTime to,
        boolean bounded,
        int maxRows,
        Break firstBreak,
        OffsetDateTime verifiedAt
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Break(
            UUID rowId,
            OffsetDateTime createdAt,
            String breakType,
            String expectedHash,
            String actualHash
    ) {
    }
}
