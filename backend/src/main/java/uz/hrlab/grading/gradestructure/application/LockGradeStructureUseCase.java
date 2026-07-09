package uz.hrlab.grading.gradestructure.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
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

/** APPROVED → LOCKED. Permission {@code GRADE_STRUCTURE_LOCK}. */
@Service
public class LockGradeStructureUseCase {

    private final GradeStructureRepository structures;
    private final GradeStructureStatusTransitionPolicy transitionPolicy;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public LockGradeStructureUseCase(GradeStructureRepository structures,
                                     GradeStructureStatusTransitionPolicy transitionPolicy,
                                     AbacGate abacGate,
                                     AuditService audit,
                                     GradeStructureAuditSnapshot snapshot) {
        this.structures = structures;
        this.transitionPolicy = transitionPolicy;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public GradeStructure lock(UUID structureId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_STRUCTURE_LOCK);
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(structureId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (s.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, s.getProjectId());
        }
        transitionPolicy.check(s.getStatus(), GradeStructureTransition.LOCK);

        var before = snapshot.of(s);
        OffsetDateTime now = OffsetDateTime.now();
        s.setStatus(GradeStructureStatus.LOCKED);
        s.setLockedAt(now);
        s.setLockedBy(ctx.userId());
        structures.save(s);

        audit.record(AuditEvent.builder(ctx)
                .projectId(s.getProjectId())
                .action(AuditAction.GRADE_STRUCTURE_LOCKED)
                .entityType("GradeStructure")
                .entityId(structureId)
                .beforeJson(before)
                .afterJson(snapshot.of(s))
                .build());
        return s.toDomain();
    }
}
