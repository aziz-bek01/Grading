package uz.hrlab.grading.audit.infrastructure;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MVP1-E10-1 — lightweight projection of an audit row for the verifier's
 * <b>pass 1</b> (chain-order reconstruction). Carries only the columns needed to
 * follow the hash links and classify each row — NO {@code before_json}/{@code
 * after_json} payload — so the whole bounded chain can be loaded to build the
 * link map while payload memory stays O(page) (fetched a page at a time in
 * pass 2). See {@code AuditChainVerifier}.
 */
public record AuditChainNode(
        UUID id,
        String hashPrev,
        String hashCurrent,
        short hashFormatVersion,
        OffsetDateTime createdAt
) {
}
