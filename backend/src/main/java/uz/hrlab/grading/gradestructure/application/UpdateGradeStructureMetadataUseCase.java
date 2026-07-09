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
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Metadata-only update (name + description). Works regardless of status —
 * cosmetic edits to translation maps don't affect scoring behavior.
 */
@Service
public class UpdateGradeStructureMetadataUseCase {

    private final GradeStructureRepository structures;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;

    public UpdateGradeStructureMetadataUseCase(GradeStructureRepository structures,
                                               AbacGate abacGate,
                                               AuditService audit,
                                               GradeStructureAuditSnapshot snapshot) {
        this.structures = structures;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public GradeStructure update(UUID id, Map<String, String> nameI18n,
                                  Map<String, String> descriptionI18n) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_EDIT);
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(id, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (s.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, s.getProjectId());
        }

        var before = snapshot.of(s);
        if (nameI18n != null && !nameI18n.isEmpty()) s.setNameI18n(nameI18n);
        if (descriptionI18n != null) s.setDescriptionI18n(descriptionI18n);
        structures.save(s);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(s.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.GRADE_STRUCTURE_UPDATED)
                .entityType("GradeStructure")
                .entityId(s.getId())
                .beforeJson(before)
                .afterJson(snapshot.of(s))
                .build());
        return s.toDomain();
    }
}
