package uz.hrlab.grading.workflow.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.UUID;

/**
 * Read-only count queries used by {@link WorkflowRecomputeService}.
 *
 * <p>Kept as a single component instead of bloating each module's repository
 * because the workflow module is the consumer and these counts are domain-
 * specific to the recompute algorithm. Tenant-scoped JPQL only — never trusts
 * caller-supplied IDs without a tenant filter.
 */
@Component
public class WorkflowEntityCounts {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public long countDepartments(UUID tenantId, UUID projectId) {
        return scalar("""
                SELECT COUNT(d) FROM DepartmentJpaEntity d
                 WHERE d.tenantId = :tid AND d.projectId = :pid
                """, tenantId, projectId);
    }

    @Transactional(readOnly = true)
    public long countPositions(UUID tenantId, UUID projectId) {
        return scalar("""
                SELECT COUNT(p) FROM PositionJpaEntity p
                 WHERE p.tenantId = :tid AND p.projectId = :pid
                """, tenantId, projectId);
    }

    @Transactional(readOnly = true)
    public long countActivePositions(UUID tenantId, UUID projectId) {
        return scalar("""
                SELECT COUNT(p) FROM PositionJpaEntity p
                 WHERE p.tenantId = :tid AND p.projectId = :pid
                   AND p.status = uz.hrlab.grading.position.domain.PositionStatus.ACTIVE
                """, tenantId, projectId);
    }

    @Transactional(readOnly = true)
    public long countApprovedJobProfiles(UUID tenantId, UUID projectId) {
        return scalar("""
                SELECT COUNT(jp) FROM JobProfileJpaEntity jp
                 WHERE jp.tenantId = :tid AND jp.projectId = :pid
                   AND jp.status = uz.hrlab.grading.jobprofile.domain.JobProfileStatus.APPROVED
                """, tenantId, projectId);
    }

    @Transactional(readOnly = true)
    public long countLockedMethodologyVersionsForProject(UUID tenantId, UUID projectId) {
        // A project becomes "methodology-ready" when at least one MethodologyVersion
        // is in LOCKED status whose parent methodology is project-scoped, OR which is
        // referenced by the project's methodology_version_id.
        //
        // NOTE: project_id lives on MethodologyJpaEntity (the aggregate root), NOT on
        // MethodologyVersionJpaEntity — the version only carries methodology_id. The
        // previous query referenced the non-existent mv.projectId attribute and threw
        // UnknownPathException (HTTP 500 on every workflow-progress call). We now reach
        // the project scope through the parent methodology via mv.methodologyId.
        return scalar("""
                SELECT COUNT(mv) FROM MethodologyVersionJpaEntity mv
                 WHERE mv.tenantId = :tid
                   AND mv.status = uz.hrlab.grading.methodology.domain.MethodologyVersionStatus.LOCKED
                   AND (EXISTS (SELECT 1 FROM MethodologyJpaEntity m
                                 WHERE m.id = mv.methodologyId AND m.projectId = :pid)
                        OR EXISTS (SELECT 1 FROM ProjectJpaEntity p
                                    WHERE p.id = :pid AND p.methodologyVersionId = mv.id))
                """, tenantId, projectId);
    }

    @Transactional(readOnly = true)
    public long countEvaluationsByApprovedOrLocked(UUID tenantId, UUID projectId) {
        return scalar("""
                SELECT COUNT(e) FROM EvaluationJpaEntity e
                 WHERE e.tenantId = :tid AND e.projectId = :pid
                   AND e.status IN (
                        uz.hrlab.grading.evaluation.domain.EvaluationStatus.APPROVED,
                        uz.hrlab.grading.evaluation.domain.EvaluationStatus.LOCKED)
                """, tenantId, projectId);
    }

    @Transactional(readOnly = true)
    public long countEvaluationsInProject(UUID tenantId, UUID projectId) {
        return scalar("""
                SELECT COUNT(e) FROM EvaluationJpaEntity e
                 WHERE e.tenantId = :tid AND e.projectId = :pid
                """, tenantId, projectId);
    }

    /** SUBMITTED or COMPLETE evaluations still waiting on calibration/approval. */
    @Transactional(readOnly = true)
    public long countEvaluationsAwaitingCalibration(UUID tenantId, UUID projectId) {
        return scalar("""
                SELECT COUNT(e) FROM EvaluationJpaEntity e
                 WHERE e.tenantId = :tid AND e.projectId = :pid
                   AND e.status IN (
                        uz.hrlab.grading.evaluation.domain.EvaluationStatus.SUBMITTED,
                        uz.hrlab.grading.evaluation.domain.EvaluationStatus.COMPLETE)
                """, tenantId, projectId);
    }

    @Transactional(readOnly = true)
    public long countLockedGradeStructures(UUID tenantId, UUID projectId) {
        return scalar("""
                SELECT COUNT(gs) FROM GradeStructureJpaEntity gs
                 WHERE gs.tenantId = :tid AND gs.projectId = :pid
                   AND gs.status = uz.hrlab.grading.gradestructure.domain.GradeStructureStatus.LOCKED
                """, tenantId, projectId);
    }

    private long scalar(String jpql, UUID tenantId, UUID projectId) {
        Object res = em.createQuery(jpql)
                .setParameter("tid", tenantId)
                .setParameter("pid", projectId)
                .getSingleResult();
        return res == null ? 0L : ((Number) res).longValue();
    }
}
