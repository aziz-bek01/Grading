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
import uz.hrlab.grading.evaluation.domain.Evaluation;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.methodology.domain.MethodologyVersionStatus;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionJpaEntity;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Create a new evaluation in DRAFT state for a position + methodology version.
 *
 * <p>Pre-conditions enforced:
 * <ol>
 *   <li>Tenant context has {@code EVALUATION_EDIT}.</li>
 *   <li>Position belongs to the tenant.</li>
 *   <li>MethodologyVersion belongs to the tenant and is APPROVED or LOCKED
 *       (cannot evaluate against a DRAFT methodology).</li>
 *   <li>ABAC write-path check on the position's project AND department
 *       (E4-S3): a department-scoped caller ({@code DEPARTMENT_MANAGER} /
 *       {@code EVALUATION_COMMITTEE_MEMBER}) may only create an evaluation for
 *       a position INSIDE their assigned department subtree. A position outside
 *       the subtree is rejected with a 404-equivalent
 *       {@code TenantAccessDeniedException} (no existence reveal) and an
 *       {@code ACCESS_DENIED_BY_ABAC} audit row. Tenant-wide / bypass roles are
 *       unaffected.</li>
 *   <li>No active (non-ARCHIVED) evaluation already exists for the same
 *       (position, methodologyVersion) — the partial unique index also
 *       enforces this at the DB level.</li>
 * </ol>
 */
@Service
public class CreateEvaluationUseCase {

    private final EvaluationRepository evaluations;
    private final PositionRepository positions;
    private final MethodologyVersionRepository versions;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final EvaluationAuditSnapshot snapshot;

    public CreateEvaluationUseCase(EvaluationRepository evaluations,
                                   PositionRepository positions,
                                   MethodologyVersionRepository versions,
                                   AbacGate abacGate,
                                   AuditService audit,
                                   EvaluationAuditSnapshot snapshot) {
        this.evaluations = evaluations;
        this.positions = positions;
        this.versions = versions;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
    }

    @Transactional
    public Evaluation create(CreateEvaluationCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.EVALUATION_EDIT);
        if (cmd == null || cmd.positionId() == null || cmd.methodologyVersionId() == null) {
            throw new ValidationException("positionId and methodologyVersionId are required");
        }
        PositionJpaEntity position = positions.findByIdAndTenantId(cmd.positionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        MethodologyVersionJpaEntity version = versions
                .findByIdAndTenantId(cmd.methodologyVersionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (version.getStatus() != MethodologyVersionStatus.APPROVED
                && version.getStatus() != MethodologyVersionStatus.LOCKED) {
            throw new ValidationException(
                    "Evaluation can only be created against APPROVED or LOCKED methodology versions");
        }
        // E4-S3 — project membership + department-subtree write gate. Department
        // is the position's; a department-scoped caller acting on a position
        // outside their subtree is denied (404, audited ACCESS_DENIED_BY_ABAC).
        abacGate.enforceCanWriteInDepartment(
                ctx, position.getProjectId(), position.getDepartmentId());

        UUID evaluator = cmd.evaluatorUserId() != null ? cmd.evaluatorUserId() : ctx.userId();

        // MVP2 multi-evaluator — the uniqueness moved UP to the panel level. A
        // panel-seat evaluation guards one-active-per-(panel, evaluator); a
        // panelless single evaluation keeps the legacy one-active-per-(position,
        // methodology version) guard. The relaxed DB unique index is the source
        // of truth in both cases; these are the friendly app-layer pre-checks.
        if (cmd.panelId() != null) {
            boolean seatExists = evaluations
                    .existsByTenantIdAndPanelIdAndEvaluatorUserIdAndStatusNot(
                            ctx.tenantId(), cmd.panelId(), evaluator, EvaluationStatus.ARCHIVED);
            if (seatExists) {
                throw new ValidationException(
                        "An active evaluation already exists for this evaluator on the panel");
            }
        } else {
            boolean activeExists = evaluations
                    .existsByTenantIdAndPositionIdAndMethodologyVersionIdAndStatusNot(
                            ctx.tenantId(), position.getId(), version.getId(),
                            EvaluationStatus.ARCHIVED);
            if (activeExists) {
                throw new ValidationException(
                        "An active evaluation already exists for this position and methodology version");
            }
        }

        EvaluationJpaEntity entity = new EvaluationJpaEntity(
                UUID.randomUUID(),
                ctx.tenantId(),
                position.getProjectId(),
                position.getId(),
                version.getId(),
                evaluator,
                EvaluationStatus.DRAFT);
        entity.setPanelId(cmd.panelId());
        entity.setEvaluatorRole(cmd.evaluatorRole());
        evaluations.save(entity);

        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .projectId(position.getProjectId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_CREATED)
                .entityType("Evaluation")
                .entityId(entity.getId())
                .afterJson(snapshot.of(entity))
                .build());
        return entity.toDomain();
    }
}
