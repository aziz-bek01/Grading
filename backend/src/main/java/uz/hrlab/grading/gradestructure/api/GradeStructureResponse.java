package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.domain.GradeStructure;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Grade structure wire shape. Single-item paths use the lean {@link #from}
 * factory (gradeCount/createdAt/updatedAt are null); the list path uses
 * {@link #fromList} which adds {@code grade_count} + audit timestamps so the FE
 * list/banner has no phantom-field drift (BE-1). Mirrors
 * MethodologyResponse.from / fromList.
 *
 * <p>NOTE: approved_by_name / locked_by_name / archive_reason are intentionally
 * NOT on the wire — archive reason lives in the audit event only.
 */
public record GradeStructureResponse(
        UUID id,
        UUID projectId,
        String code,
        Map<String, String> nameI18n,
        Map<String, String> descriptionI18n,
        String structureType,
        String status,
        int versionNumber,
        UUID previousVersionId,
        String gapPolicy,
        OffsetDateTime approvedAt,
        UUID approvedBy,
        OffsetDateTime lockedAt,
        UUID lockedBy,
        OffsetDateTime archivedAt,
        UUID archivedBy,
        // List-view enrichment (null on single-item create/get/update paths).
        // Serialize as grade_count / created_at / updated_at.
        Integer gradeCount,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static GradeStructureResponse from(GradeStructure s) {
        return new GradeStructureResponse(
                s.id(), s.projectId(), s.code(), s.nameI18n(), s.descriptionI18n(),
                s.structureType().name(), s.status().name(),
                s.versionNumber(), s.previousVersionId(),
                s.gapPolicy().name(),
                s.approvedAt(), s.approvedBy(),
                s.lockedAt(), s.lockedBy(),
                s.archivedAt(), s.archivedBy(),
                null, null, null);
    }

    /**
     * List-view factory: enriches the base structure with {@code grade_count}
     * and the audit timestamps the lean {@link #from} factory omits.
     *
     * @param e          the grade-structure row.
     * @param gradeCount number of grades under {@code e} (from the batched
     *                   count query — no N+1).
     */
    public static GradeStructureResponse fromList(GradeStructureJpaEntity e, int gradeCount) {
        GradeStructure s = e.toDomain();
        return new GradeStructureResponse(
                s.id(), s.projectId(), s.code(), s.nameI18n(), s.descriptionI18n(),
                s.structureType().name(), s.status().name(),
                s.versionNumber(), s.previousVersionId(),
                s.gapPolicy().name(),
                s.approvedAt(), s.approvedBy(),
                s.lockedAt(), s.lockedBy(),
                s.archivedAt(), s.archivedBy(),
                gradeCount, e.getCreatedAt(), e.getUpdatedAt());
    }
}
