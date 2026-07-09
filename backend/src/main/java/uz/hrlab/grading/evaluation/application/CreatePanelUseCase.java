package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * BE-4 — open an evaluation panel in {@code COLLECTING}.
 *
 * <p>Pre-conditions (mirror {@code CreateEvaluationUseCase}):
 * <ol>
 *   <li>Caller holds {@code EVALUATION_PANEL_MANAGE}.</li>
 *   <li>Position + methodology version belong to the tenant.</li>
 *   <li>Methodology version is APPROVED or LOCKED (cannot evaluate against DRAFT).</li>
 *   <li>ABAC write gate on the position's department (out-of-subtree → 404 +
 *       {@code ACCESS_DENIED_BY_ABAC}).</li>
 *   <li>No active (non-ARCHIVED) panel already exists for this (position,
 *       methodology version) — the relocated uniqueness; the DB partial unique
 *       index {@code uq_panels_active_per_position_version} is the source of
 *       truth, this is the friendly pre-check.</li>
 * </ol>
 *
 * <p>The old "already exists" guard that lived on evaluation-create now lives
 * HERE at the panel level; per-evaluator create guards one-active-evaluation-
 * per-(panel, evaluator).
 */
@Service
public class CreatePanelUseCase {

    /** Product floor — minimum mandatory-role evaluators (HR Dir + Dept Dir + Expert). */
    public static final int DEFAULT_MIN_EVALUATORS = 3;

    private final PanelRepository panels;
    private final PositionRepository positions;
    private final MethodologyVersionRepository versions;
    private final AbacGate abacGate;
    private final AuditService audit;

    public CreatePanelUseCase(PanelRepository panels,
                              PositionRepository positions,
                              MethodologyVersionRepository versions,
                              AbacGate abacGate,
                              AuditService audit) {
        this.panels = panels;
        this.positions = positions;
        this.versions = versions;
        this.abacGate = abacGate;
        this.audit = audit;
    }

    @Transactional
    public EvaluationPanel create(CreatePanelCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_PANEL_MANAGE);
        if (cmd == null || cmd.positionId() == null || cmd.methodologyVersionId() == null) {
            throw new ValidationException("positionId and methodologyVersionId are required");
        }
        int minEvaluators = cmd.minEvaluators() == null
                ? DEFAULT_MIN_EVALUATORS : cmd.minEvaluators();
        if (minEvaluators < 1) {
            throw new ValidationException("minEvaluators must be >= 1");
        }

        PositionJpaEntity position = positions.findByIdAndTenantId(cmd.positionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyVersionJpaEntity version = versions
                .findByIdAndTenantId(cmd.methodologyVersionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (version.getStatus() != MethodologyVersionStatus.APPROVED
                && version.getStatus() != MethodologyVersionStatus.LOCKED) {
            throw new ValidationException(
                    "Panel can only be created against APPROVED or LOCKED methodology versions");
        }
        abacGate.enforceCanWriteInDepartment(
                ctx, position.getProjectId(), position.getDepartmentId());

        boolean activeExists = panels
                .existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
                        ctx.tenantId(), position.getId(), version.getId(),
                        EvaluationPanelStatus.ARCHIVED);
        if (activeExists) {
            throw new ValidationException(
                    "An active panel already exists for this position and methodology version");
        }

        EvaluationPanelJpaEntity entity = new EvaluationPanelJpaEntity(
                UUID.randomUUID(),
                ctx.tenantId(),
                position.getProjectId(),
                position.getId(),
                version.getId(),
                EvaluationPanelStatus.COLLECTING,
                minEvaluators);
        panels.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(position.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_PANEL_CREATED)
                .entityType("EvaluationPanel")
                .entityId(entity.getId())
                .reason("minEvaluators=" + minEvaluators)
                .build());
        return entity.toDomain();
    }
}
