package uz.hrlab.grading.jobprofile.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.access.domain.ApprovedEntityFilterPolicy;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * M-1 — position-scoped job-profile reads must apply the approved-status filter
 * for status-restricted roles, exactly as the singular {@code GET
 * /job-profiles/{id}} path already does.
 *
 * <p>Unlike {@link FindJobProfileStatusesQueryTest} (which mocks the gate to
 * isolate the batch mechanics), this test wires the REAL {@link AbacGate} with
 * the REAL {@link ApprovedEntityFilterPolicy} so the role → status decision is
 * genuinely exercised. Only the repositories are mocked.
 *
 * <p>Fail-closed expectation for {@code EXTERNAL_AUDITOR}/Viewer:
 * <ul>
 *   <li>a DRAFT/UNDER_REVIEW profile is WITHHELD — absent on the bulk path,
 *       {@link Optional#empty()} (→ 204) on the singular position path;</li>
 *   <li>an APPROVED profile is returned.</li>
 * </ul>
 * A Consultant/HR role is {@code NOT_APPLICABLE} to the filter and still sees
 * drafts (no over-tightening).
 */
@Tag("security")
@Tag("abac")
@ExtendWith(MockitoExtension.class)
class FindJobProfileApprovedStatusFilterTest {

    @Mock JobProfileRepository profiles;
    @Mock PositionRepository positions;
    @Mock AuditService audit;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();
    private final UUID positionId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    /** Query wired with the REAL gate + REAL approved-status policy. */
    private FindJobProfileQuery query() {
        AbacGate gate = new AbacGate(List.of(new ApprovedEntityFilterPolicy()), audit);
        return new FindJobProfileQuery(profiles, positions, gate);
    }

    private void activateRole(String role) {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId),
                Set.of(role), Set.of("JOB_PROFILE_READ"),
                Set.of(), false, "ru-RU"));
    }

    private PositionJpaEntity position() {
        return new PositionJpaEntity(positionId, tenantId, projectId, departmentId,
                "POS-1", Map.of("ru-RU", "Позиция"),
                null, null, null, null, PositionStatus.ACTIVE);
    }

    private JobProfileJpaEntity profile(JobProfileStatus status) {
        return new JobProfileJpaEntity(UUID.randomUUID(), tenantId, projectId, positionId,
                status, 1, null);
    }

    private void stubSingular(JobProfileStatus status) {
        when(positions.findByIdAndTenantId(eq(positionId), eq(tenantId)))
                .thenReturn(Optional.of(position()));
        when(profiles.findFirstByTenantIdAndProjectIdAndPositionIdAndStatusNot(
                eq(tenantId), eq(projectId), eq(positionId), eq(JobProfileStatus.ARCHIVED)))
                .thenReturn(Optional.of(profile(status)));
    }

    private void stubBulk(JobProfileStatus status) {
        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position()));
        when(profiles.findAllByTenantIdAndPositionIdInAndStatusNot(
                eq(tenantId), any(), eq(JobProfileStatus.ARCHIVED)))
                .thenReturn(List.of(profile(status)));
    }

    // --- singular position path (GET /positions/{id}/job-profile) -----------

    @Test
    void auditorDraftWithheldOnSingularPath() {
        activateRole(RoleCodes.EXTERNAL_AUDITOR);
        stubSingular(JobProfileStatus.DRAFT);

        Optional<JobProfile> result = query().findActiveByPositionId(positionId);

        assertThat(result).isEmpty(); // withheld → 204/absent
    }

    @Test
    void auditorApprovedReturnedOnSingularPath() {
        activateRole(RoleCodes.EXTERNAL_AUDITOR);
        stubSingular(JobProfileStatus.APPROVED);

        Optional<JobProfile> result = query().findActiveByPositionId(positionId);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(JobProfileStatus.APPROVED);
    }

    @Test
    void consultantDraftStillVisibleOnSingularPath() {
        activateRole(RoleCodes.HRLAB_CONSULTANT);
        stubSingular(JobProfileStatus.DRAFT);

        Optional<JobProfile> result = query().findActiveByPositionId(positionId);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(JobProfileStatus.DRAFT);
    }

    // --- bulk path (GET /job-profiles/statuses) -----------------------------

    @Test
    void auditorDraftWithheldOnBulkPath() {
        activateRole(RoleCodes.EXTERNAL_AUDITOR);
        stubBulk(JobProfileStatus.DRAFT);

        List<JobProfile> result = query().findActiveStatusesByPositionIds(List.of(positionId));

        assertThat(result).isEmpty(); // withheld → absent from the page
    }

    @Test
    void auditorApprovedReturnedOnBulkPath() {
        activateRole(RoleCodes.EXTERNAL_AUDITOR);
        stubBulk(JobProfileStatus.APPROVED);

        List<JobProfile> result = query().findActiveStatusesByPositionIds(List.of(positionId));

        assertThat(result).extracting(JobProfile::status)
                .containsExactly(JobProfileStatus.APPROVED);
    }

    @Test
    void consultantDraftStillVisibleOnBulkPath() {
        activateRole(RoleCodes.HRLAB_CONSULTANT);
        stubBulk(JobProfileStatus.DRAFT);

        List<JobProfile> result = query().findActiveStatusesByPositionIds(List.of(positionId));

        assertThat(result).extracting(JobProfile::status)
                .containsExactly(JobProfileStatus.DRAFT);
    }
}
