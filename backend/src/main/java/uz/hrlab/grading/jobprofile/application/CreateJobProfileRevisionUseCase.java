package uz.hrlab.grading.jobprofile.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatusTransitionPolicy;
import uz.hrlab.grading.jobprofile.domain.JobProfileTransition;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * APPROVED → new DRAFT (PRD §E6 hard rule), implemented as <b>archive-on-revision</b>.
 *
 * <p>In a single transaction the source APPROVED row is moved to ARCHIVED and a
 * new {@link JobProfileJpaEntity} DRAFT is created with
 * {@code revisionNumber = source.revisionNumber + 1} and
 * {@code previousRevisionId = source.id}. Content is deep-copied so the new
 * revision starts from the approved content.
 *
 * <p>Archiving the source is required to satisfy the partial unique index
 * {@code uq_job_profiles_position_active}
 * ({@code UNIQUE (tenant_id, project_id, position_id) WHERE status <> 'ARCHIVED'}),
 * which permits at most ONE non-ARCHIVED job_profile per (tenant, project,
 * position). After this use case runs the only non-ARCHIVED row is the new
 * DRAFT, so the index is satisfied without any migration.
 *
 * <p>Two audit rows are emitted: {@code JOB_PROFILE_ARCHIVED} for the source and
 * {@code JOB_PROFILE_REVISION_CREATED} for the new revision.
 */
@Service
public class CreateJobProfileRevisionUseCase {

    private final JobProfileRepository profiles;
    private final PositionRepository positions;
    private final AuditService audit;
    private final AbacGate abacGate;
    private final JobProfileStatusTransitionPolicy transitionPolicy;
    private final JobProfileAuditSnapshot snapshot;

    @PersistenceContext
    private EntityManager em;

    public CreateJobProfileRevisionUseCase(JobProfileRepository profiles,
                                           PositionRepository positions,
                                           AuditService audit,
                                           AbacGate abacGate,
                                           JobProfileStatusTransitionPolicy transitionPolicy,
                                           JobProfileAuditSnapshot snapshot) {
        this.profiles = profiles;
        this.positions = positions;
        this.audit = audit;
        this.abacGate = abacGate;
        this.transitionPolicy = transitionPolicy;
        this.snapshot = snapshot;
    }

    @Transactional
    public JobProfile createRevision(UUID sourceId) {
        TenantContext ctx = TenantContextHolder.requireActive();
        JobProfileJpaEntity source = profiles.findByIdAndTenantId(sourceId, ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        PositionJpaEntity position = positions
                .findByIdAndTenantId(source.getPositionId(), ctx.tenantId())
                .orElseThrow(TenantAccessDeniedException::new);

        abacGate.enforceCanWriteInProject(ctx, source.getProjectId());
        abacGate.enforceCanWriteInDepartment(ctx, source.getProjectId(),
                position.getDepartmentId());

        transitionPolicy.check(source.getStatus(), JobProfileTransition.CREATE_REVISION);

        // Archive the source FIRST so the partial unique index
        // (one non-ARCHIVED profile per position) is never violated by the
        // new DRAFT insert below — both happen in this single transaction.
        var sourceBeforeJson = snapshot.of(source);
        int newRevisionNumber = source.getRevisionNumber() + 1;
        source.setStatus(JobProfileStatus.ARCHIVED);
        source.setLockedAt(OffsetDateTime.now());
        profiles.save(source);
        // Force the APPROVED→ARCHIVED UPDATE to hit the DB BEFORE the new DRAFT
        // INSERT below. Hibernate's default action-queue ordering flushes inserts
        // before updates, which would momentarily leave two non-ARCHIVED rows and
        // trip the partial unique index uq_job_profiles_position_active. Flushing
        // here makes the archive durable first so the index is always satisfied.
        em.flush();

        audit.record(AuditEvent.builder(ctx)
                .projectId(source.getProjectId())
                .action(AuditAction.JOB_PROFILE_ARCHIVED)
                .entityType("JobProfile")
                .entityId(source.getId())
                .reason("Superseded by revision " + newRevisionNumber)
                .beforeJson(sourceBeforeJson)
                .afterJson(snapshot.of(source))
                .build());

        UUID newId = UUID.randomUUID();
        JobProfileJpaEntity revision = new JobProfileJpaEntity(
                newId, ctx.tenantId(), source.getProjectId(), source.getPositionId(),
                JobProfileStatus.DRAFT,
                newRevisionNumber,
                source.getId());
        // Deep-copy content so the new revision is a working draft.
        revision.setPurposeI18n(source.getPurposeI18n());
        revision.setMainDutiesI18n(source.getMainDutiesI18n());
        revision.setResponsibilityAreaI18n(source.getResponsibilityAreaI18n());
        revision.setAuthorityI18n(source.getAuthorityI18n());
        revision.setKpiExpectedResultsI18n(source.getKpiExpectedResultsI18n());
        revision.setEducationRequirementsI18n(source.getEducationRequirementsI18n());
        revision.setExperienceRequirementsI18n(source.getExperienceRequirementsI18n());
        revision.setKnowledgeSkillsI18n(source.getKnowledgeSkillsI18n());
        revision.setInternalInteractionsI18n(source.getInternalInteractionsI18n());
        revision.setExternalInteractionsI18n(source.getExternalInteractionsI18n());
        revision.setWorkingConditionsI18n(source.getWorkingConditionsI18n());
        revision.setDocumentsRegulationsI18n(source.getDocumentsRegulationsI18n());
        revision.setActualizationDate(source.getActualizationDate());
        profiles.save(revision);

        audit.record(AuditEvent.builder(ctx)
                .projectId(source.getProjectId())
                .action(AuditAction.JOB_PROFILE_REVISION_CREATED)
                .entityType("JobProfile")
                .entityId(newId)
                .reason("Revision of " + source.getId())
                .beforeJson(null)
                .afterJson(snapshot.of(revision))
                .build());
        return revision.toDomain();
    }
}
