package uz.hrlab.grading.approval.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.domain.ApprovalStepStatus;
import uz.hrlab.grading.approval.domain.ApprovalTransitionRejectedException;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalStepRepository;
import java.util.List;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Opens an approval request for a target entity. Enforces:
 * <ul>
 *   <li>Caller has APPROVAL_REQUEST_CREATE (controller + use case).</li>
 *   <li>Project belongs to caller's tenant.</li>
 *   <li>No other PENDING request exists for the same (entity_type, entity_id)
 *       — DB partial unique index is the source of truth; check here first
 *       for a friendly error.</li>
 *   <li>At least one step is provided.</li>
 * </ul>
 */
@Service
public class CreateApprovalRequestUseCase {

    private final ApprovalRequestRepository requests;
    private final ApprovalStepRepository steps;
    private final ProjectRepository projects;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final ApprovalQueries queries;

    public CreateApprovalRequestUseCase(ApprovalRequestRepository requests,
                                        ApprovalStepRepository steps,
                                        ProjectRepository projects,
                                        AbacGate abacGate,
                                        AuditService audit,
                                        ApprovalQueries queries) {
        this.requests = requests;
        this.steps = steps;
        this.projects = projects;
        this.abacGate = abacGate;
        this.audit = audit;
        this.queries = queries;
    }

    @Transactional
    public ApprovalRequest create(CreateApprovalRequestCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.APPROVAL_REQUEST_CREATE);
        return createInternal(ctx, cmd, true);
    }

    /**
     * Internal entry point used by sister use cases (e.g.
     * {@code SubmitJobProfileForReviewUseCase}) so that the inner caller has
     * already established the active context and authorized the originating
     * action; the approval-create permission is NOT separately re-checked.
     */
    @Transactional
    public ApprovalRequest createSystem(CreateApprovalRequestCommand cmd) {
        TenantContext ctx = TenantContextHolder.requireActive();
        return createInternal(ctx, cmd, false);
    }

    /**
     * Opens an approval request AND immediately marks its first (single) step
     * as APPROVED + the overall request status APPROVED. Used by
     * {@code Approve*UseCase} when the originating user acted as both the
     * submitter and the deciding authority — Methodology and GradeStructure
     * have a DRAFT→APPROVED state machine (no intermediate UNDER_REVIEW) so
     * the approval is recorded post-hoc for the audit trail and inbox
     * consistency (PO-9). The caller has already passed the relevant
     * {@code *_APPROVE} permission, so no extra permission re-check is
     * performed here. Idempotent: if a PENDING request already exists for the
     * same entity, this is a no-op (returns null) so re-running the approve
     * action does not double-record.
     */
    @Transactional
    public ApprovalRequest createSystemAndAutoApproveFirstStep(CreateApprovalRequestCommand cmd,
                                                               Map<String, String> decisionReasonI18n) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (cmd == null || cmd.projectId() == null) {
            // System-level (no project) entities are not auto-recorded — skip.
            return null;
        }
        try {
            ApprovalRequest created = createInternal(ctx, cmd, false);
            // Mark the single step APPROVED.
            List<ApprovalStepJpaEntity> stepRows = steps
                    .findAllByTenantIdAndApprovalRequestIdOrderByStepOrderAsc(
                            ctx.tenantId(), created.id());
            if (stepRows.isEmpty()) return created;
            ApprovalStepJpaEntity firstStep = stepRows.get(0);
            OffsetDateTime now = OffsetDateTime.now();
            firstStep.setStatus(ApprovalStepStatus.APPROVED);
            firstStep.setDecidedBy(ctx.userId());
            firstStep.setDecidedAt(now);
            if (decisionReasonI18n != null && !decisionReasonI18n.isEmpty()) {
                firstStep.setReasonI18n(decisionReasonI18n);
            }
            steps.save(firstStep);

            // Flip the request status to APPROVED (single-step request — last
            // step approving means the whole request is approved).
            ApprovalRequestJpaEntity req = requests
                    .findByIdAndTenantId(created.id(), ctx.tenantId()).orElseThrow();
            req.setCurrentStatus(ApprovalRequestStatus.APPROVED);
            requests.save(req);

            audit.record(AuditEvent.builder(ctx)
                    .projectId(req.getProjectId())
                    .action(AuditAction.APPROVAL_STEP_APPROVED)
                    .entityType("ApprovalStep")
                    .entityId(firstStep.getId())
                    .reason("auto-approved on direct " + cmd.entityType() + " approval")
                    .build());
            return queries.hydrate(req);
        } catch (ApprovalTransitionRejectedException existing) {
            if ("ANOTHER_PENDING_REQUEST_EXISTS".equals(existing.getCode())) {
                // Idempotent — a previous run already opened a request.
                return null;
            }
            throw existing;
        }
    }

    private ApprovalRequest createInternal(TenantContext ctx, CreateApprovalRequestCommand cmd,
                                           boolean enforceProjectAbac) {
        if (cmd == null || cmd.projectId() == null || cmd.entityType() == null
                || cmd.entityId() == null) {
            throw new ValidationException("MISSING_FIELDS");
        }
        if (cmd.steps() == null || cmd.steps().isEmpty()) {
            throw new ValidationException("STEPS_REQUIRED");
        }
        projects.findByIdAndTenantId(cmd.projectId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        // Project-membership ABAC is a USER-action gate: enforced ONLY on the public
        // create() path. The createSystem* / internal variants run AFTER the
        // originating action already authorized the caller (their javadoc contract),
        // or are trusted system sweeps that run under a permission-less system
        // context (the startup CEO-approval reconciliation) — they must NOT be
        // denied by project-membership ABAC. Tenant isolation is still enforced for
        // ALL paths by findByIdAndTenantId above, so a cross-tenant project still
        // 404s. (Without this, the reconciliation's system context — no roles, no
        // projectIds — was denied by ProjectMembershipPolicy and silently opened
        // zero approvals, leaving fully-evaluated panels invisible to the CEO.)
        if (enforceProjectAbac) {
            abacGate.enforceCanWriteInProject(ctx, cmd.projectId());
        }

        // Reject if another request is already PENDING for this entity.
        if (queries.findActivePendingForEntity(ctx.tenantId(), cmd.entityType(), cmd.entityId())
                != null) {
            throw new ApprovalTransitionRejectedException("ANOTHER_PENDING_REQUEST_EXISTS",
                    "Another pending approval request already exists for this entity");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ApprovalRequestJpaEntity req = new ApprovalRequestJpaEntity(
                UUID.randomUUID(), ctx.tenantId(), cmd.projectId(),
                cmd.entityType(), cmd.entityId(), ctx.userId(), now,
                ApprovalRequestStatus.PENDING, cmd.notesI18n());
        requests.save(req);

        for (CreateApprovalRequestCommand.StepSpec spec : cmd.steps()) {
            if (spec.requiredPermission() == null || spec.requiredPermission().isBlank()) {
                throw new ValidationException("STEP_PERMISSION_REQUIRED");
            }
            ApprovalStepJpaEntity step = new ApprovalStepJpaEntity(
                    UUID.randomUUID(), ctx.tenantId(), req.getId(),
                    spec.stepOrder(), spec.approverUserId(),
                    spec.requiredPermission(), ApprovalStepStatus.PENDING);
            steps.save(step);
        }

        audit.record(AuditEvent.builder(ctx)
                .projectId(cmd.projectId())
                .action(AuditAction.APPROVAL_REQUEST_CREATED)
                .entityType("ApprovalRequest")
                .entityId(req.getId())
                .reason("target=" + cmd.entityType() + ":" + cmd.entityId())
                .build());

        return queries.hydrate(req);
    }
}
