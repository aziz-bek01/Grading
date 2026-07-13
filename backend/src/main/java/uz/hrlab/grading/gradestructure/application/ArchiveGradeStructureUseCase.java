package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.application.StatusTransitionExecutor;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.gradestructure.domain.GradeStructure;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatusTransitionPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureTransition;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/** ARCHIVE — terminal. Reason required (min 20 chars to match calibration convention). */
@Service
public class ArchiveGradeStructureUseCase {

    public static final int MIN_REASON_LENGTH = 20;

    private final GradeStructureRepository structures;
    private final GradeStructureStatusTransitionPolicy transitionPolicy;
    private final GradeStructureAuditSnapshot snapshot;
    private final StatusTransitionExecutor transitions;

    public ArchiveGradeStructureUseCase(GradeStructureRepository structures,
                                        GradeStructureStatusTransitionPolicy transitionPolicy,
                                        AbacGate abacGate,
                                        AuditService audit,
                                        GradeStructureAuditSnapshot snapshot) {
        this.structures = structures;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
        this.transitions = new StatusTransitionExecutor(abacGate, audit);
    }

    @Transactional
    public GradeStructure archive(UUID structureId, String reason) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_EDIT);
        if (reason == null || reason.trim().length() < MIN_REASON_LENGTH) {
            throw new ValidationException(
                    "reason is required (min " + MIN_REASON_LENGTH + " chars)");
        }
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(structureId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        OffsetDateTime now = OffsetDateTime.now();
        transitions.transition(ctx)
                .abacProjectWrite(s.getProjectId())
                .checkTransition(() -> transitionPolicy.check(s.getStatus(), GradeStructureTransition.ARCHIVE))
                .snapshot(() -> snapshot.of(s))
                .mutate(() -> {
                    s.setStatus(GradeStructureStatus.ARCHIVED);
                    s.setArchivedAt(now);
                    s.setArchivedBy(ctx.userId());
                })
                .save(() -> structures.save(s))
                .reason(reason)
                .audit(AuditAction.GRADE_STRUCTURE_ARCHIVED, "GradeStructure",
                        structureId, s.getProjectId())
                .execute();
        return s.toDomain();
    }
}
