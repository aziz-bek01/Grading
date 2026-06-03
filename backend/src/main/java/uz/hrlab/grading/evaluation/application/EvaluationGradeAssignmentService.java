package uz.hrlab.grading.evaluation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.gradestructure.application.GradeBandLookupService;
import uz.hrlab.grading.gradestructure.application.GradeStructureQueries;
import uz.hrlab.grading.gradestructure.domain.GradeBandLookupResult;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureJpaEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Phase 6 integration helper: maps an evaluation's raw total score to a grade
 * via {@link GradeBandLookupService}. Used by both:
 * <ul>
 *   <li>{@link ApproveEvaluationUseCase} (initial auto-assignment)</li>
 *   <li>{@link CalibrateEvaluationScoreUseCase} (re-assignment after score change)</li>
 * </ul>
 *
 * <p>If no active grade structure exists for the project, or the score falls
 * outside all bands (or into a gap), the evaluation's {@code gradeBandId} +
 * {@code assignedGradeNumber} stay null. The integration logs a warning and
 * does NOT block evaluation approval (manual calibration is the recovery path).
 */
@Service
public class EvaluationGradeAssignmentService {

    private static final Logger log =
            LoggerFactory.getLogger(EvaluationGradeAssignmentService.class);

    private final GradeStructureQueries gradeStructureQueries;
    private final GradeBandLookupService lookupService;
    private final AuditService audit;

    public EvaluationGradeAssignmentService(GradeStructureQueries gradeStructureQueries,
                                            GradeBandLookupService lookupService,
                                            AuditService audit) {
        this.gradeStructureQueries = gradeStructureQueries;
        this.lookupService = lookupService;
        this.audit = audit;
    }

    /**
     * Look up the grade for the evaluation's current rawTotalScore and write
     * the result onto the entity. Emits {@code GRADE_ASSIGNED} when a grade is
     * found, or logs a warning otherwise.
     *
     * @param evaluation     the entity (mutated in place)
     * @param actorUserId    the acting user (for audit)
     * @return the previous gradeBandId before assignment (may be null)
     */
    public UUID assignFromScore(EvaluationJpaEntity evaluation, UUID actorUserId) {
        UUID previousBandId = evaluation.getGradeBandId();
        Integer previousGradeNumber = evaluation.getAssignedGradeNumber();

        Optional<GradeStructureJpaEntity> structOpt = gradeStructureQueries
                .findActiveForProject(evaluation.getTenantId(), evaluation.getProjectId());
        if (structOpt.isEmpty()) {
            log.warn("Evaluation {} approved without active grade structure for project {} — "
                    + "grade left unassigned (manual calibration required).",
                    evaluation.getId(), evaluation.getProjectId());
            evaluation.setGradeBandId(null);
            evaluation.setAssignedGradeNumber(null);
            return previousBandId;
        }
        GradeStructureJpaEntity structure = structOpt.get();
        Optional<GradeBandLookupResult> result = lookupService.lookup(
                evaluation.getTenantId(), structure.getId(), evaluation.getRawTotalScore());

        if (result.isEmpty()) {
            log.warn("Evaluation {} score {} out of range / in gap of grade structure {} — "
                    + "grade left unassigned (manual calibration required).",
                    evaluation.getId(), evaluation.getRawTotalScore(), structure.getId());
            evaluation.setGradeBandId(null);
            evaluation.setAssignedGradeNumber(null);
            return previousBandId;
        }
        GradeBandLookupResult r = result.get();
        evaluation.setGradeBandId(r.gradeBandId());
        evaluation.setAssignedGradeNumber(r.gradeNumber());

        boolean firstAssignment = previousBandId == null;
        boolean changed = !firstAssignment && !r.gradeBandId().equals(previousBandId);
        String action = firstAssignment ? AuditAction.GRADE_ASSIGNED
                : (changed ? AuditAction.GRADE_REASSIGNED : null);
        if (action != null) {
            audit.record(AuditEvent.builder()
                    .tenantId(evaluation.getTenantId())
                    .projectId(evaluation.getProjectId())
                    .actorUserId(actorUserId)
                    .action(action)
                    .entityType("Evaluation")
                    .entityId(evaluation.getId())
                    .reason("score=" + evaluation.getRawTotalScore()
                            + ";grade=" + r.gradeNumber()
                            + (previousGradeNumber == null ? "" : ";prev=" + previousGradeNumber)
                            + ";structure=" + structure.getId())
                    .build());
        }
        return previousBandId;
    }
}
