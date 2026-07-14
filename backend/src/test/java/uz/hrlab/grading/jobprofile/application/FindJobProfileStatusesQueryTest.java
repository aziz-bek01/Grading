package uz.hrlab.grading.jobprofile.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PAGE-2 BE — unit test for {@link FindJobProfileQuery#findActiveStatusesByPositionIds}:
 * the bulk "active job-profile status by position ids" batch read that replaces
 * the frontend per-position fan-out.
 *
 * <p>Asserts the four behaviours the endpoint contract depends on:
 * <ul>
 *   <li>empty / oversized input is rejected BEFORE any tenant resolution or DB
 *       call ({@link ValidationException} → 400);</li>
 *   <li>the active profile per position is loaded in ONE tenant-scoped batch
 *       query (no per-id loop) and positions with no active profile are absent;</li>
 *   <li>tenant scoping uses {@code TenantContext.tenantId()} on BOTH batch
 *       queries — never a request-supplied value;</li>
 *   <li>ABAC-denied positions are OMITTED (same gate as the per-position read),
 *       never surfaced, and never sent to the job-profile query.</li>
 * </ul>
 */
@Tag("security")
@Tag("abac")
@ExtendWith(MockitoExtension.class)
class FindJobProfileStatusesQueryTest {

    @Mock JobProfileRepository profiles;
    @Mock PositionRepository positions;
    @Mock AbacGate abacGate;

    private FindJobProfileQuery query;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID departmentId = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    private FindJobProfileQuery newQuery() {
        return new FindJobProfileQuery(profiles, positions, abacGate);
    }

    private void activateTenant() {
        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId, Set.of(projectId),
                Set.of("HRLAB_PROJECT_MANAGER"), Set.of("JOB_PROFILE_READ"),
                Set.of(), false, "ru-RU"));
    }

    private PositionJpaEntity position(UUID id) {
        return new PositionJpaEntity(id, tenantId, projectId, departmentId,
                "POS-" + id.toString().substring(0, 4), Map.of("ru-RU", "Позиция"),
                null, null, null, null, PositionStatus.ACTIVE);
    }

    private JobProfileJpaEntity activeProfile(UUID positionId, JobProfileStatus status) {
        return new JobProfileJpaEntity(UUID.randomUUID(), tenantId, projectId, positionId,
                status, 1, null);
    }

    @Test
    void nullInputRejectedBeforeAnyLookup() {
        query = newQuery();
        assertThatThrownBy(() -> query.findActiveStatusesByPositionIds(null))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "POSITION_IDS_REQUIRED");
        verifyNoInteractions(positions, profiles, abacGate);
    }

    @Test
    void emptyInputRejectedBeforeAnyLookup() {
        query = newQuery();
        assertThatThrownBy(() -> query.findActiveStatusesByPositionIds(List.of()))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "POSITION_IDS_REQUIRED");
        verifyNoInteractions(positions, profiles, abacGate);
    }

    @Test
    void oversizedInputRejectedBeforeAnyLookup() {
        query = newQuery();
        List<UUID> tooMany = IntStream.range(0, 201)
                .mapToObj(i -> UUID.randomUUID())
                .toList();
        assertThatThrownBy(() -> query.findActiveStatusesByPositionIds(tooMany))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "POSITION_IDS_LIMIT_EXCEEDED");
        verifyNoInteractions(positions, profiles, abacGate);
    }

    @Test
    void loadsActiveProfilesInOneBatchAndOmitsPositionsWithoutOne() {
        query = newQuery();
        activateTenant();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID p3 = UUID.randomUUID(); // has no active profile → absent from result

        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position(p1), position(p2), position(p3)));
        // ONE batch query returns only the two positions that actually have an
        // active profile — p3 is simply not in the result set.
        when(profiles.findAllByTenantIdAndPositionIdInAndStatusNot(
                eq(tenantId), any(), eq(JobProfileStatus.ARCHIVED)))
                .thenReturn(List.of(
                        activeProfile(p1, JobProfileStatus.APPROVED),
                        activeProfile(p2, JobProfileStatus.DRAFT)));

        List<JobProfile> result =
                query.findActiveStatusesByPositionIds(List.of(p1, p2, p3));

        assertThat(result).extracting(JobProfile::positionId)
                .containsExactlyInAnyOrder(p1, p2);
        assertThat(result).extracting(JobProfile::status)
                .containsExactlyInAnyOrder(JobProfileStatus.APPROVED, JobProfileStatus.DRAFT);

        // No per-id N+1: exactly ONE position batch + ONE profile batch query.
        verify(positions).findAllByTenantIdAndIdIn(eq(tenantId), any());
        verify(profiles).findAllByTenantIdAndPositionIdInAndStatusNot(
                eq(tenantId), any(), eq(JobProfileStatus.ARCHIVED));
        verify(profiles, never()).findFirstByTenantIdAndProjectIdAndPositionIdAndStatusNot(
                any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenantScopingUsesContextTenantOnBothBatchQueries() {
        query = newQuery();
        activateTenant();
        UUID p1 = UUID.randomUUID();

        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position(p1)));
        when(profiles.findAllByTenantIdAndPositionIdInAndStatusNot(
                eq(tenantId), any(), eq(JobProfileStatus.ARCHIVED)))
                .thenReturn(List.of(activeProfile(p1, JobProfileStatus.APPROVED)));

        query.findActiveStatusesByPositionIds(List.of(p1));

        ArgumentCaptor<UUID> posTenant = ArgumentCaptor.forClass(UUID.class);
        verify(positions).findAllByTenantIdAndIdIn(posTenant.capture(), any());
        assertThat(posTenant.getValue()).isEqualTo(tenantId);

        ArgumentCaptor<UUID> profTenant = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Collection<UUID>> profIds = ArgumentCaptor.forClass(Collection.class);
        verify(profiles).findAllByTenantIdAndPositionIdInAndStatusNot(
                profTenant.capture(), profIds.capture(), eq(JobProfileStatus.ARCHIVED));
        assertThat(profTenant.getValue()).isEqualTo(tenantId);
        assertThat(profIds.getValue()).containsExactly(p1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void abacDeniedPositionsAreOmittedAndNeverQueriedForProfiles() {
        query = newQuery();
        activateTenant();
        UUID readable = UUID.randomUUID();
        UUID denied = UUID.randomUUID();

        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position(readable), position(denied)));
        // Same gate as the per-position read denies the out-of-scope position.
        // lenient(): the readable position's enforce call intentionally does NOT
        // match this stub (strict stubbing would otherwise flag it).
        lenient().doThrow(new TenantAccessDeniedException()).when(abacGate)
                .enforceCanReadPosition(any(), eq(denied), any(), any(), any());
        when(profiles.findAllByTenantIdAndPositionIdInAndStatusNot(
                eq(tenantId), any(), eq(JobProfileStatus.ARCHIVED)))
                .thenReturn(List.of(activeProfile(readable, JobProfileStatus.APPROVED)));

        List<JobProfile> result =
                query.findActiveStatusesByPositionIds(List.of(readable, denied));

        assertThat(result).extracting(JobProfile::positionId).containsExactly(readable);

        // The denied id is never handed to the job-profile batch query.
        ArgumentCaptor<Collection<UUID>> profIds = ArgumentCaptor.forClass(Collection.class);
        verify(profiles).findAllByTenantIdAndPositionIdInAndStatusNot(
                eq(tenantId), profIds.capture(), eq(JobProfileStatus.ARCHIVED));
        assertThat(profIds.getValue()).containsExactly(readable);
    }

    @Test
    void allPositionsDeniedReturnsEmptyWithoutProfileQuery() {
        query = newQuery();
        activateTenant();
        UUID p1 = UUID.randomUUID();

        when(positions.findAllByTenantIdAndIdIn(eq(tenantId), any()))
                .thenReturn(List.of(position(p1)));
        doThrow(new TenantAccessDeniedException()).when(abacGate)
                .enforceCanReadPosition(any(), eq(p1), any(), any(), any());

        List<JobProfile> result = query.findActiveStatusesByPositionIds(List.of(p1));

        assertThat(result).isEmpty();
        verify(profiles, never()).findAllByTenantIdAndPositionIdInAndStatusNot(
                any(), any(), any());
    }
}
