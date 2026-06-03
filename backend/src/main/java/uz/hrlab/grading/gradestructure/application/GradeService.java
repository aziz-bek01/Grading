package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.gradestructure.domain.Grade;
import uz.hrlab.grading.gradestructure.domain.GradeStructureImmutabilityPolicy;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * CRUD on individual {@link GradeJpaEntity} rows under a parent
 * {@link GradeStructureJpaEntity}. Only allowed while parent is DRAFT —
 * enforced both at the service layer (immutability policy) and the DB trigger
 * {@code prevent_grade_changes_on_locked_structure}.
 */
@Service
public class GradeService {

    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;
    private final GradeStructureImmutabilityPolicy immutability;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public GradeService(GradeStructureRepository structures,
                        GradeRepository grades,
                        GradeBandRepository bands,
                        GradeStructureImmutabilityPolicy immutability,
                        AbacGate abacGate,
                        AuditService audit,
                        GradeStructureAuditSnapshot snapshot) {
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
        this.immutability = immutability;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public Grade addGrade(UUID structureId, GradeCommand.Create cmd) {
        TenantContext ctx = requireEdit();
        GradeStructureJpaEntity s = loadAndCheck(ctx, structureId);
        immutability.ensureMutable(s.getStatus());

        if (grades.existsByTenantIdAndGradeStructureIdAndGradeNumber(
                ctx.tenantId(), structureId, cmd.gradeNumber())) {
            throw new ValidationException("GRADE_NUMBER_DUPLICATE",
                    "Grade number already exists in this structure");
        }

        UUID gradeId = UUID.randomUUID();
        GradeJpaEntity g = new GradeJpaEntity(gradeId, ctx.tenantId(), structureId,
                cmd.gradeNumber(), cmd.sortOrder());
        g.setNameI18n(cmd.nameI18n());
        g.setDescriptionI18n(cmd.descriptionI18n());
        grades.save(g);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(s.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_CREATED)
                .entityType("Grade")
                .entityId(gradeId)
                .afterJson(snapshot.of(g))
                .build());
        return g.toDomain();
    }

    @Transactional
    public Grade updateGrade(UUID gradeId, GradeCommand.Update cmd) {
        TenantContext ctx = requireEdit();
        GradeJpaEntity g = grades.findByIdAndTenantId(gradeId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        GradeStructureJpaEntity s = loadAndCheck(ctx, g.getGradeStructureId());
        immutability.ensureMutable(s.getStatus());

        var before = snapshot.of(g);
        g.setGradeNumber(cmd.gradeNumber());
        g.setSortOrder(cmd.sortOrder());
        if (cmd.nameI18n() != null) g.setNameI18n(cmd.nameI18n());
        if (cmd.descriptionI18n() != null) g.setDescriptionI18n(cmd.descriptionI18n());
        grades.save(g);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(s.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_UPDATED)
                .entityType("Grade")
                .entityId(g.getId())
                .beforeJson(before)
                .afterJson(snapshot.of(g))
                .build());
        return g.toDomain();
    }

    @Transactional
    public void removeGrade(UUID gradeId) {
        TenantContext ctx = requireEdit();
        GradeJpaEntity g = grades.findByIdAndTenantId(gradeId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        GradeStructureJpaEntity s = loadAndCheck(ctx, g.getGradeStructureId());
        immutability.ensureMutable(s.getStatus());

        var before = snapshot.of(g);
        // Remove the band first (FK).
        bands.findByTenantIdAndGradeId(ctx.tenantId(), gradeId).ifPresent(bands::delete);
        grades.delete(g);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(s.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_REMOVED)
                .entityType("Grade")
                .entityId(gradeId)
                .beforeJson(before)
                .build());
    }

    @Transactional
    public GradeBandJpaEntity upsertBand(UUID gradeId, GradeCommand.UpsertBand cmd) {
        TenantContext ctx = requireEdit();
        if (cmd.minScore() == null || cmd.maxScore() == null) {
            throw new ValidationException("GRADE_BAND_RANGE_REQUIRED",
                    "minScore and maxScore are required");
        }
        if (cmd.minScore().compareTo(cmd.maxScore()) > 0) {
            throw new ValidationException("GRADE_BAND_RANGE_INVALID",
                    "minScore must be <= maxScore");
        }
        GradeJpaEntity g = grades.findByIdAndTenantId(gradeId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        GradeStructureJpaEntity s = loadAndCheck(ctx, g.getGradeStructureId());
        immutability.ensureMutable(s.getStatus());

        GradeBandJpaEntity existing = bands
                .findByTenantIdAndGradeId(ctx.tenantId(), gradeId).orElse(null);
        var before = existing == null ? null : snapshot.of(existing);
        if (existing == null) {
            existing = new GradeBandJpaEntity(UUID.randomUUID(), ctx.tenantId(), gradeId,
                    g.getGradeStructureId(), cmd.minScore(), cmd.maxScore());
        } else {
            existing.setMinScore(cmd.minScore());
            existing.setMaxScore(cmd.maxScore());
        }
        bands.save(existing);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(s.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_BAND_UPSERTED)
                .entityType("GradeBand")
                .entityId(existing.getId())
                .beforeJson(before)
                .afterJson(snapshot.of(existing))
                .build());
        return existing;
    }

    private TenantContext requireEdit() {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.GRADE_EDIT)) {
            throw new PermissionDeniedException();
        }
        return ctx;
    }

    private GradeStructureJpaEntity loadAndCheck(TenantContext ctx, UUID structureId) {
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(structureId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (s.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, s.getProjectId());
        }
        return s;
    }
}
