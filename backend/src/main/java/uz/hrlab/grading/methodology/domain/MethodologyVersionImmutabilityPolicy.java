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
}
