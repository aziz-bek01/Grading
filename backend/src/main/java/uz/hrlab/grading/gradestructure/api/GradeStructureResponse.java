package uz.hrlab.grading.gradestructure.api;

import uz.hrlab.grading.gradestructure.domain.GradeStructure;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

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
        UUID archivedBy
) {
    public static GradeStructureResponse from(GradeStructure s) {
        return new GradeStructureResponse(
                s.id(), s.projectId(), s.code(), s.nameI18n(), s.descriptionI18n(),
                s.structureType().name(), s.status().name(),
                s.versionNumber(), s.previousVersionId(),
                s.gapPolicy().name(),
                s.approvedAt(), s.approvedBy(),
                s.lockedAt(), s.lockedBy(),
                s.archivedAt(), s.archivedBy());
    }
}
