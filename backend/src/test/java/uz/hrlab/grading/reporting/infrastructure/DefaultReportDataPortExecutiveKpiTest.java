package uz.hrlab.grading.reporting.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogJpaEntity;
import uz.hrlab.grading.audit.infrastructure.SystemAuditLogRepository;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationRepository;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationScoreRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeRepository;
import uz.hrlab.grading.gradestructure.infrastructure.GradeStructureRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorLevelRepository;
import uz.hrlab.grading.methodology.infrastructure.FactorRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyRepository;
import uz.hrlab.grading.methodology.infrastructure.MethodologyVersionRepository;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.infrastructure.ProjectRepository;
import uz.hrlab.grading.reporting.application.template.EvaluationReportFilter;
import uz.hrlab.grading.reporting.application.template.ReportDataPort;
import uz.hrlab.grading.tenancy.infrastructure.TenantRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link DefaultReportDataPort#loadExecutiveKpi} filter scoping.
 * Proves the evaluation-scoped KPIs reflect the FILTERED set (via the existing
 * {@code findForEvaluationReport} finder) while positionCount / auditEventCount
 * stay project-wide, and that an empty filter takes the legacy unfiltered path.
 */
class DefaultReportDataPortExecutiveKpiTest {

    private final PositionRepository positions = mock(PositionRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final EvaluationRepository evaluations = mock(EvaluationRepository.class);
    private final EvaluationScoreRepository evaluationScores = mock(EvaluationScoreRepository.class);
    private final FactorRepository factors = mock(FactorRepository.class);
    private final FactorLevelRepository factorLevels = mock(FactorLevelRepository.class);
    private final MethodologyVersionRepository methodologyVersions =
            mock(MethodologyVersionRepository.class);
    private final MethodologyRepository methodologies = mock(MethodologyRepository.class);
    private final GradeStructureRepository gradeStructures = mock(GradeStructureRepository.class);
    private final GradeRepository grades = mock(GradeRepository.class);
    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final SystemAuditLogRepository auditLog = mock(SystemAuditLogRepository.class);
    private final ActorNameResolver actorNames = mock(ActorNameResolver.class);

    private final DefaultReportDataPort port = new DefaultReportDataPort(
            positions, projects, evaluations, evaluationScores, factors, factorLevels,
            methodologyVersions, methodologies, gradeStructures, grades, departments,
            users, tenants, auditLog, actorNames);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @Test
    void emptyFilterUsesUnfilteredFinderAndPreservesLegacyKpis() {
        // positionCount (project-wide) = 42; audit-count (project-wide) = 7.
        stubProjectWideKpis(42, 7);
        // The UNFILTERED finder returns the full project evaluation set.
        Page<EvaluationJpaEntity> fullSet = page(
                ev(EvaluationStatus.APPROVED, 3),
                ev(EvaluationStatus.LOCKED, 4),
                ev(EvaluationStatus.DRAFT, null),
                ev(EvaluationStatus.SUBMITTED, 5));
        when(evaluations.findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any()))
                .thenReturn(fullSet);

        ReportDataPort.ExecutiveKpi kpi =
                port.loadExecutiveKpi(tenantId, projectId, "en-US", EvaluationReportFilter.none());

        assertThat(kpi.positionCount()).isEqualTo(42);
        assertThat(kpi.auditEventCount()).isEqualTo(7);
        assertThat(kpi.evaluatedCount()).isEqualTo(4);
        assertThat(kpi.approvedEvaluationCount()).isEqualTo(2);   // APPROVED + LOCKED
        assertThat(kpi.gradeCount()).isEqualTo(3);                // distinct {3,4,5}
        assertThat(kpi.activeFilters().hasPeriod()).isFalse();
        assertThat(kpi.activeFilters().hasEvaluators()).isFalse();
        assertThat(kpi.activeFilters().hasMethodologies()).isFalse();

        // The unfiltered legacy path is used; the filtered finder is NEVER called.
        verify(evaluations, times(1)).findAllByTenantIdAndProjectId(eq(tenantId), eq(projectId), any());
        verify(evaluations, never()).findForEvaluationReport(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonEmptyFilterScopesEvaluationKpisButLeavesPositionAndAuditProjectWide() {
        // positionCount (project-wide) = 42; audit-count (project-wide) = 7 —
        // these must NOT change just because the filter narrows evaluations.
        stubProjectWideKpis(42, 7);
        // The FILTERED finder returns a narrower evaluation set.
        Page<EvaluationJpaEntity> filteredSet = page(
                ev(EvaluationStatus.APPROVED, 4),
                ev(EvaluationStatus.SUBMITTED, 4));
        when(evaluations.findForEvaluationReport(
                eq(tenantId), eq(projectId), any(), any(), any(), any(), any()))
                .thenReturn(filteredSet);

        OffsetDateTime from = OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2026, 6, 30, 23, 59, 59, 0, ZoneOffset.UTC);
        EvaluationReportFilter filter = new EvaluationReportFilter(
                List.of(), from, to, List.of(UUID.randomUUID()));

        ReportDataPort.ExecutiveKpi kpi =
                port.loadExecutiveKpi(tenantId, projectId, "en-US", filter);

        // Evaluation-scoped KPIs reflect the FILTERED set.
        assertThat(kpi.evaluatedCount()).isEqualTo(2);
        assertThat(kpi.approvedEvaluationCount()).isEqualTo(1);   // only the APPROVED one
        assertThat(kpi.gradeCount()).isEqualTo(1);                // distinct {4} (4 appears twice)
        // Non-evaluation KPIs stay project-wide.
        assertThat(kpi.positionCount()).isEqualTo(42);
        assertThat(kpi.auditEventCount()).isEqualTo(7);
        // FilterEcho carries the period (name resolution stays inside the port).
        assertThat(kpi.activeFilters().hasPeriod()).isTrue();
        assertThat(kpi.activeFilters().period()).isEqualTo("2026-04-01 – 2026-06-30");
        assertThat(kpi.activeFilters().hasEvaluators()).isTrue();

        // Only the FILTERED finder is used; the legacy unfiltered finder is NOT.
        verify(evaluations, times(1)).findForEvaluationReport(
                eq(tenantId), eq(projectId), any(), any(), any(), any(), any());
        verify(evaluations, never()).findAllByTenantIdAndProjectId(any(), any(), any());
    }

    // --- helpers ----------------------------------------------------------

    /** Stub the non-evaluation, project-wide KPI sources (positions + audit). */
    private void stubProjectWideKpis(long positionCount, long auditCount) {
        when(projects.findByIdAndTenantId(projectId, tenantId)).thenReturn(java.util.Optional.empty());
        when(positions.search(eq(tenantId), eq(projectId), any(), any(), any(), any()))
                .thenReturn(emptyPageWithTotal(positionCount));
        // auditLog.search(...) default delegates; with the interface mocked it is
        // itself a stubbable method. recentApprovals stays empty (we only assert count).
        when(auditLog.search(eq(tenantId), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyAuditPageWithTotal(auditCount));
    }

    private Page<EvaluationJpaEntity> page(EvaluationJpaEntity... evs) {
        List<EvaluationJpaEntity> content = List.of(evs);
        return new PageImpl<>(content, PageRequest.of(0, 1_000), content.size());
    }

    private Page<PositionJpaEntity> emptyPageWithTotal(long total) {
        return new PageImpl<>(List.of(), PageRequest.of(0, 1), total);
    }

    private Page<SystemAuditLogJpaEntity> emptyAuditPageWithTotal(long total) {
        return new PageImpl<>(List.of(), PageRequest.of(0, 200), total);
    }

    private EvaluationJpaEntity ev(EvaluationStatus status, Integer gradeNumber) {
        EvaluationJpaEntity e = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), status);
        e.setAssignedGradeNumber(gradeNumber);
        return e;
    }
}
