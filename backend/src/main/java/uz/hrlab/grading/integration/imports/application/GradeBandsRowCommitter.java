package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PO-11 — GRADE_BANDS_V1 commit DAO.
 *
 * <p>Each row carries (grade_code, min_score, max_score, label). The row
 * upserts a {@link uz.hrlab.grading.gradestructure.domain.Grade} +
 * {@link uz.hrlab.grading.gradestructure.domain.GradeBand} in the project's
 * unique DRAFT GradeStructure. Constraints:
 * <ul>
 *   <li>The target GradeStructure MUST be in DRAFT status — approved /
 *       locked structures are immutable.</li>
 *   <li>Exactly one DRAFT GradeStructure must exist for the project (the
 *       template does not carry a structure code in MVP 2; multi-structure
 *       support is deferred).</li>
 *   <li>{@code grade_code} is parsed as a positive integer grade_number.</li>
 * </ul>
 */
@Component
public class GradeBandsRowCommitter implements ImportRowCommitter {

    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;
    private final AuditService audit;

    public GradeBandsRowCommitter(GradeStructureRepository structures,
                                  GradeRepository grades,
                                  GradeBandRepository bands,
                                  AuditService audit) {
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
        this.audit = audit;
    }

    @Override
    public String templateCode() {
        return ImportTemplateCode.GRADE_BANDS_V1;
    }

    @Override
    public CommitResult commit(Map<String, String> row, ImportRowCommitContext ctx) {
        if (ctx.projectId() == null) {
            throw new ImportRowCommitException("PROJECT_REQUIRED",
                    "GRADE_BANDS_V1 commit requires a project");
        }
        // Resolve the unique DRAFT GradeStructure for this project.
        List<GradeStructureJpaEntity> drafts = structures
                .findAllByTenantIdAndProjectIdAndStatusOrderByVersionNumberDesc(
                        ctx.tenantId(), ctx.projectId(), GradeStructureStatus.DRAFT);
        if (drafts.isEmpty()) {
            throw new ImportRowCommitException("NO_DRAFT_GRADE_STRUCTURE",
                    "No DRAFT GradeStructure exists in this project — create one before import");
        }
        if (drafts.size() > 1) {
            throw new ImportRowCommitException("AMBIGUOUS_GRADE_STRUCTURE",
                    "Multiple DRAFT GradeStructures exist in this project — GRADE_BANDS_V1 "
                            + "requires exactly one DRAFT target");
        }
        GradeStructureJpaEntity structure = drafts.get(0);

        int gradeNumber = parseGradeNumber(req(row, "grade_code"));
        BigDecimal minScore = parseDecimal(req(row, "min_score"), "min_score");
        BigDecimal maxScore = parseDecimal(req(row, "max_score"), "max_score");
        if (minScore.compareTo(maxScore) > 0) {
            throw new ImportRowCommitException("INVALID_BAND_RANGE",
                    "min_score must be <= max_score");
        }

        // Upsert Grade.
        Optional<GradeJpaEntity> existingGrade = grades
                .findByTenantIdAndGradeStructureIdAndGradeNumber(
                        ctx.tenantId(), structure.getId(), gradeNumber);
        GradeJpaEntity grade;
        boolean gradeCreated;
        if (existingGrade.isPresent()) {
            grade = existingGrade.get();
            gradeCreated = false;
        } else {
            grade = new GradeJpaEntity(UUID.randomUUID(), ctx.tenantId(),
                    structure.getId(), gradeNumber, gradeNumber);
            String label = row.get("label");
            if (label != null && !label.isBlank()) {
                grade.setNameI18n(Map.of("ru-RU", label.trim()));
            } else {
                grade.setNameI18n(Map.of("ru-RU", "Grade " + gradeNumber));
            }
            grades.save(grade);
            gradeCreated = true;
        }

        // Upsert Band.
        Optional<GradeBandJpaEntity> existingBand = bands
                .findByTenantIdAndGradeId(ctx.tenantId(), grade.getId());
        GradeBandJpaEntity band;
        if (existingBand.isPresent()) {
            band = existingBand.get();
            band.setMinScore(minScore);
            band.setMaxScore(maxScore);
        } else {
            band = new GradeBandJpaEntity(UUID.randomUUID(), ctx.tenantId(),
                    grade.getId(), structure.getId(), minScore, maxScore);
        }
        bands.save(band);

        if (gradeCreated) {
            audit.record(AuditEvent.builder()
                    .tenantId(ctx.tenantId())
                    .projectId(ctx.projectId())
                    .actorUserId(ctx.actorUserId())
                    .action(AuditAction.GRADE_CREATED)
                    .entityType("Grade")
                    .entityId(grade.getId())
                    .reason("import_batch=" + ctx.importBatchId())
                    .build());
        }
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(ctx.projectId())
                .actorUserId(ctx.actorUserId())
                .action(AuditAction.GRADE_BAND_UPSERTED)
                .entityType("GradeBand")
                .entityId(band.getId())
                .reason("import_batch=" + ctx.importBatchId())
                .build());

        return new CommitResult("GradeBand", band.getId(), AuditAction.GRADE_BAND_UPSERTED);
    }

    private static String req(Map<String, String> row, String key) {
        String v = row.get(key);
        if (v == null || v.isBlank()) {
            throw new ImportRowCommitException("MISSING_REQUIRED_FIELD",
                    "Required field missing: " + key);
        }
        return v.trim();
    }

    private static int parseGradeNumber(String raw) {
        try {
            int n = Integer.parseInt(raw.trim());
            if (n <= 0) {
                throw new ImportRowCommitException("INVALID_GRADE_NUMBER",
                        "grade_code must be a positive integer");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ImportRowCommitException("INVALID_GRADE_NUMBER",
                    "grade_code must be a positive integer");
        }
    }

    private static BigDecimal parseDecimal(String raw, String fieldName) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new ImportRowCommitException("INVALID_DECIMAL",
                    fieldName + " is not a valid decimal: " + raw);
        }
    }
}
