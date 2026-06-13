package uz.hrlab.grading.methodology.domain;

import org.springframework.stereotype.Component;

/**
 * Immutability gate (ADR-002): once a {@link MethodologyVersion} reaches
 * APPROVED / LOCKED / ARCHIVED, its content (factors + factor levels +
 * metadata that lives on the version row) cannot be mutated. Edits must go
 * through {@code CreateMethodologyVersionUseCase} which produces a new DRAFT
 * row chained via {@code previousVersionId}.
 *
 * <p>Service layer calls {@link #ensureMutable(MethodologyVersionStatus)} on
 * every write. The DB triggers {@code trg_factor_immutability_on_locked_version}
 * + {@code trg_level_immutability_on_locked_version} provide defence-in-depth.
 */
@Component
public class MethodologyVersionImmutabilityPolicy {

    /**
     * @throws MethodologyVersionTransitionRejectedException when the version
     *         is not in DRAFT.
     */
    public void ensureMutable(MethodologyVersionStatus status) {
        if (status != MethodologyVersionStatus.DRAFT) {
            throw new MethodologyVersionTransitionRejectedException(
                    "Cannot modify a methodology version in state " + status.name()
                            + " — create a new version (CREATE_NEW_VERSION) first");
        }
    }

    /**
     * Approved-edit carve-out (BE-2). Single source of the status × permission
     * decision for factor / level writes:
     * <ul>
     *   <li>{@code DRAFT}    — always mutable (existing {@code METHODOLOGY_EDIT}
     *       behaviour; {@code canEditApproved} irrelevant).</li>
     *   <li>{@code APPROVED} — mutable ONLY when {@code canEditApproved} is true
     *       (the caller holds {@code METHODOLOGY_EDIT_APPROVED}); otherwise
     *       rejected. The DB trigger carve-out (changelog 042) provides
     *       defence-in-depth, gated by the {@code app.methodology_approved_edit}
     *       GUC the service sets on this branch only.</li>
     *   <li>{@code LOCKED} / {@code ARCHIVED} — always rejected, regardless of
     *       any permission (hard immutable by design).</li>
     * </ul>
     *
     * @param status           the methodology version status
     * @param canEditApproved  whether the caller holds
     *                         {@code METHODOLOGY_EDIT_APPROVED}
     * @return {@code true} when the write targets an APPROVED version under the
     *         carve-out (the caller MUST then set the transaction GUC before the
     *         write); {@code false} for the ordinary DRAFT path (no GUC).
     * @throws MethodologyVersionTransitionRejectedException when the write is
     *         not permitted for this status / permission combination.
     */
    public boolean ensureMutableOrApprovedEdit(MethodologyVersionStatus status,
                                               boolean canEditApproved) {
        if (status == MethodologyVersionStatus.DRAFT) {
            return false;
        }
        if (status == MethodologyVersionStatus.APPROVED && canEditApproved) {
            return true;
        }
        throw new MethodologyVersionTransitionRejectedException(
                "Cannot modify a methodology version in state " + status.name()
                        + (status == MethodologyVersionStatus.APPROVED
                            ? " — METHODOLOGY_EDIT_APPROVED is required to edit an approved version"
                            : " — create a new version (CREATE_NEW_VERSION) first"));
    }
}
