package uz.hrlab.grading.audit.application;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MVP1-E10-1 — outcome of an audit hash-chain integrity verification for ONE
 * tenant chain (or a windowed slice of it). Application-layer value object;
 * mapped to a snake_case wire DTO at the API edge.
 *
 * <p><b>Hash-format versioning.</b> Append-only LINKAGE ({@code hash_prev} ==
 * prior stored {@code hash_current}) is checked for EVERY row regardless of
 * format, so a genuine linkage break is always caught. Content RECOMPUTE is only
 * possible for rows written in the current canonical format
 * ({@link AuditHashCalculator#HASH_FORMAT_VERSION}); pre-hardening (v1) rows are
 * counted as {@code legacyUnverifiableCount} — linkage-intact but not
 * independently content-verifiable — and are NEVER reported as tampered.
 *
 * @param tenantId       the verified tenant chain (sourced from TenantContext)
 * @param status         INTACT / BROKEN / EMPTY
 * @param checkedCount   rows the verifier inspected (linkage-checked). For an
 *                       intact, un-truncated run this equals chainLength and
 *                       equals independentlyVerifiedCount + legacyUnverifiableCount
 * @param chainLength    total rows in the tenant chain within the window
 * @param independentlyVerifiedCount rows whose content hash was recomputed and
 *                       matched (current-format rows)
 * @param legacyUnverifiableCount rows whose linkage held but whose content is
 *                       not independently recomputable (legacy-format rows)
 * @param verifiableFrom {@code created_at} of the first current-format row (the
 *                       point from which the chain is independently hash-
 *                       verifiable); null when no current-format row was seen
 * @param verifiedThrough {@code created_at} of the last row that passed its
 *                        applicable checks (null when nothing passed)
 * @param from           echoed window lower bound (null = from genesis)
 * @param to             echoed window upper bound (null = to head)
 * @param bounded        true when the run stopped at the max-rows cap before
 *                       reaching the end of the chain (result is partial)
 * @param maxRows        the effective per-run row cap that was applied
 * @param firstBreak     first detected anomaly, or null when INTACT/EMPTY
 * @param verifiedAt     when this verification ran
 */
public record AuditChainVerificationResult(
        UUID tenantId,
        Status status,
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

    public boolean intact() {
        return status == Status.INTACT || status == Status.EMPTY;
    }

    public enum Status {
        /** Linkage unbroken end-to-end AND every current-format row re-hashed OK. */
        INTACT,
        /** An anomaly was found — see {@link #firstBreak()}. */
        BROKEN,
        /** No rows exist in the tenant chain / window. */
        EMPTY
    }

    public enum BreakType {
        /** Stored {@code hash_current} != recomputed hash → row payload was altered
         *  (current-format rows only — legacy rows are never recomputed). */
        HASH_MISMATCH,
        /** A row does not chain from the genesis by its links: {@code hash_prev}
         *  points nowhere in the chain, or two rows share a {@code hash_prev}
         *  (fork), or the links cycle. */
        BROKEN_PREV_LINK,
        /** A predecessor is missing: no genesis (null {@code hash_prev}) row exists
         *  where one is required. */
        GAP,
        /** F-2 — {@code hash_format_version} DECREASED along the chain in link
         *  order (a v2→v1 downgrade relabel used to skip content recompute and hide
         *  a payload edit). The migration makes version non-decreasing on a
         *  legitimate chain, so a regression is a tamper. */
        VERSION_REGRESSION
    }

    /**
     * The first offending row.
     *
     * @param rowId        the offending row's surrogate id
     * @param createdAt    the offending row's timestamp (chain sequence proxy)
     * @param breakType    kind of anomaly
     * @param expectedHash for HASH_MISMATCH: the recomputed hash; for
     *                     BROKEN_PREV_LINK/GAP: the {@code hash_current} the
     *                     link SHOULD have pointed at (null for a genesis gap)
     * @param actualHash   for HASH_MISMATCH: the stored {@code hash_current};
     *                     for BROKEN_PREV_LINK/GAP: the row's stored
     *                     {@code hash_prev}
     */
    public record Break(
            UUID rowId,
            OffsetDateTime createdAt,
            BreakType breakType,
            String expectedHash,
            String actualHash
    ) {
    }
}
