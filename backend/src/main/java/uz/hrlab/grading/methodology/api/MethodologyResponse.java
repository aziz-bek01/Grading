package uz.hrlab.grading.methodology.api;

import uz.hrlab.grading.methodology.domain.Methodology;
import uz.hrlab.grading.methodology.domain.MethodologyStatus;
import uz.hrlab.grading.methodology.domain.MethodologyType;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.infrastructure.MethodologyJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MethodologyResponse(
        UUID id,
        UUID projectId,
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        MethodologyType methodologyType,
        MethodologyStatus status,
        // Nullable: only populated on create paths so the FE can navigate to the
        // version editor. Serializes as `latest_version_id` (SNAKE_CASE).
        UUID latestVersionId,
        // List-view enrichment (null on single-item create/get/update paths).
        // "active" = the latest APPROVED or LOCKED version, if any. Serialize as
        // active_version_id / active_version_number / active_version_status.
        UUID activeVersionId,
        Integer activeVersionNumber,
        String activeVersionStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static MethodologyResponse from(Methodology m) {
        return from(m, null);
    }

    public static MethodologyResponse from(Methodology m, UUID latestVersionId) {
        return new MethodologyResponse(m.id(), m.projectId(), m.code(),
                m.nameI18n(), m.descriptionI18n(),
                m.methodologyType(), m.status(), latestVersionId,
                null, null, null, null, null);
    }

    /**
     * List-view factory: enriches the base methodology with version-derived
     * pointers and audit timestamps the lean {@link #from} factories omit.
     *
     * @param e           the methodology row.
     * @param versionsDesc all of {@code e}'s versions ordered version-number
     *                     DESC (as returned by the batched repository query);
     *                     may be empty for a methodology with no versions yet.
     *                     latest = first element; active = first APPROVED/LOCKED.
     */
    public static MethodologyResponse fromList(
            MethodologyJpaEntity e, List<MethodologyVersionJpaEntity> versionsDesc) {
        Methodology m = e.toDomain();
        UUID latestVersionId = versionsDesc.isEmpty() ? null : versionsDesc.get(0).getId();
        MethodologyVersionJpaEntity active = versionsDesc.stream()
                .filter(v -> v.getStatus() == MethodologyVersionStatus.APPROVED
                        || v.getStatus() == MethodologyVersionStatus.LOCKED)
                .findFirst()
                .orElse(null);
        return new MethodologyResponse(m.id(), m.projectId(), m.code(),
                m.nameI18n(), m.descriptionI18n(),
                m.methodologyType(), m.status(), latestVersionId,
                active == null ? null : active.getId(),
                active == null ? null : active.getVersionNumber(),
                active == null ? null : active.getStatus().name(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
