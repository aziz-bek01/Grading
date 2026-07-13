package uz.hrlab.grading.gradestructure.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.gradestructure.domain.GradeBand;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapDetector;
import uz.hrlab.grading.gradestructure.domain.GradeBandGapPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeBandOverlapValidator;
import uz.hrlab.grading.gradestructure.domain.GradeStructure;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatus;
import uz.hrlab.grading.gradestructure.domain.GradeStructureStatusTransitionPolicy;
import uz.hrlab.grading.gradestructure.domain.GradeStructureTransition;
import uz.hrlab.grading.gradestructure.domain.GradeStructureTransitionRejectedException;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeBandRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DRAFT → APPROVED. Validates:
 * <ol>
 *   <li>state transition allowed</li>
 *   <li>at least one grade</li>
 *   <li>every grade has a band</li>
 *   <li>no overlapping bands</li>
 *   <li>per gap policy: STRICT_NO_GAPS rejects gaps; ALLOW_GAPS_WARN logs</li>
 * </ol>
 * Requires permission {@code GRADE_STRUCTURE_APPROVE} re-checked server-side.
 */
@Service
public class ApproveGradeStructureUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApproveGradeStructureUseCase.class);

    private final GradeStructureRepository structures;
    private final GradeRepository grades;
    private final GradeBandRepository bands;
    private final GradeStructureStatusTransitionPolicy transitionPolicy;
    private final GradeBandOverlapValidator overlapValidator;
    private final GradeBandGapDetector gapDetector;
    private final AbacGate abacGate;
    private final AuditService audit;
    private final GradeStructureAuditSnapshot snapshot;
    private final CreateApprovalRequestUseCase createApprovalRequest;

    public ApproveGradeStructureUseCase(GradeStructureRepository structures,
                                        GradeRepository grades,
                                        GradeBandRepository bands,
                                        GradeStructureStatusTransitionPolicy transitionPolicy,
                                        GradeBandOverlapValidator overlapValidator,
                                        GradeBandGapDetector gapDetector,
                                        AbacGate abacGate,
                                        AuditService audit,
                                        GradeStructureAuditSnapshot snapshot,
                                        CreateApprovalRequestUseCase createApprovalRequest) {
        this.structures = structures;
        this.grades = grades;
        this.bands = bands;
        this.transitionPolicy = transitionPolicy;
        this.overlapValidator = overlapValidator;
        this.gapDetector = gapDetector;
        this.abacGate = abacGate;
        this.audit = audit;
        this.snapshot = snapshot;
        this.createApprovalRequest = createApprovalRequest;
    }

    @Transactional
    public GradeStructure approve(UUID structureId) {
        TenantContext ctx = TenantContextHolder.requireActive().require(PermissionCodes.GRADE_STRUCTURE_APPROVE);
        GradeStructureJpaEntity s = structures.findByIdAndTenantId(structureId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);
        if (s.getProjectId() != null) {
            abacGate.enforceCanWriteInProject(ctx, s.getProjectId());
        }
        transitionPolicy.check(s.getStatus(), GradeStructureTransition.APPROVE);

        List<GradeJpaEntity> gradeRows = grades
                .findAllByTenantIdAndGradeStructureIdOrderBySortOrderAsc(ctx.tenantId(), structureId);
        if (gradeRows.isEmpty()) {
            throw new GradeStructureTransitionRejectedException(
                    "Grade structure must have at least one grade before approval");
        }

        // BE — batch every grade's band in ONE structure-scoped query (was one
        // findByTenantIdAndGradeId per grade → N+1 on the approval hot path).
        // One band per grade (MVP 1); iterate grades in sort_order so bandList
        // keeps its original ordering for overlap/gap validation.
        Map<UUID, GradeBandJpaEntity> bandByGrade = new HashMap<>();
        for (GradeBandJpaEntity b : bands
                .findAllByTenantIdAndGradeStructureIdOrderByMinScoreAsc(ctx.tenantId(), structureId)) {
            bandByGrade.put(b.getGradeId(), b);
        }
        List<GradeBand> bandList = new ArrayList<>(gradeRows.size());
        for (GradeJpaEntity g : gradeRows) {
            GradeBandJpaEntity b = bandByGrade.get(g.getId());
            if (b == null) {
                throw new GradeStructureTransitionRejectedException(
                        "Grade " + g.getGradeNumber() + " has no band defined");
            }
            bandList.add(b.toDomain());
        }

        var conflicts = overlapValidator.findOverlaps(bandList);
        if (!conflicts.isEmpty()) {
            throw new GradeStructureTransitionRejectedException(
                    "Grade bands overlap: " + conflicts.size() + " conflict(s); first conflict "
                            + "between grade " + conflicts.get(0).gradeIdA()
                            + " and " + conflicts.get(0).gradeIdB());
        }

        var gaps = gapDetector.findGaps(bandList);
        if (!gaps.isEmpty()) {
            if (s.getGapPolicy() == GradeBandGapPolicy.STRICT_NO_GAPS) {
                throw new GradeStructureTransitionRejectedException(
                        "Grade bands have " + gaps.size() + " gap(s); gap policy is STRICT_NO_GAPS");
            }
            log.warn("Grade structure {} approved with {} band gap(s); gap policy ALLOW_GAPS_WARN",
                    structureId, gaps.size());
        }

        var before = snapshot.of(s);
        OffsetDateTime now = OffsetDateTime.now();
        s.setStatus(GradeStructureStatus.APPROVED);
        s.setApprovedAt(now);
        s.setApprovedBy(ctx.userId());
        structures.save(s);

        audit.record(AuditEvent.builder(ctx)
                .projectId(s.getProjectId())
                .action(AuditAction.GRADE_STRUCTURE_APPROVED)
                .entityType("GradeStructure")
                .entityId(structureId)
                .beforeJson(before)
                .afterJson(snapshot.of(s))
                .build());

        // PO-9: auto-record ApprovalRequest (single-step, already APPROVED) for
        // GradeStructure approvals. Skipped for tenant-level grade structures
        // (projectId == null) — they are templates, not project-scoped data.
        if (s.getProjectId() != null) {
            createApprovalRequest.createSystemAndAutoApproveFirstStep(
                    CreateApprovalRequestCommand.singleStep(
                            s.getProjectId(),
                            ApprovalEntityType.GRADE_STRUCTURE,
                            structureId,
                            PermissionCodes.GRADE_STRUCTURE_APPROVE),
                    null);
        }
        return s.toDomain();
    }
}
