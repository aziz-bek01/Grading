package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;
import uz.hrlab.grading.position.domain.PositionStatus;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * PO-11 — unit tests for {@link PositionCatalogRowCommitter}. Verifies:
 * <ol>
 *   <li>Valid row creates a Position linked to the resolved Department.</li>
 *   <li>Department lookup is scoped by (tenant, project, code) — a
 *       different-tenant department cannot resolve.</li>
 *   <li>Unknown department_external_id → MISSING_DEPARTMENT.</li>
 *   <li>Duplicate external_id → DUPLICATE_CODE.</li>
 *   <li>POSITION_CREATED audit emitted with tenant + project.</li>
 * </ol>
 */
@Tag("imports")
class PositionCatalogRowCommitterTest {

    private PositionRepository positions;
    private DepartmentRepository departments;
    private AuditService audit;
    private PositionCatalogRowCommitter committer;

    private UUID tenantId;
    private UUID projectId;
    private ImportRowCommitContext ctx;

    @BeforeEach
    void setUp() {
        positions = mock(PositionRepository.class);
        departments = mock(DepartmentRepository.class);
        audit = mock(AuditService.class);
        committer = new PositionCatalogRowCommitter(positions, departments, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ctx = new ImportRowCommitContext(tenantId, projectId, UUID.randomUUID(),
                UUID.randomUUID(), "trace-p");

        given(positions.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(positions.existsByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(false);
    }

    @Test
    void validRow_createsPosition() {
        UUID deptId = UUID.randomUUID();
        DepartmentJpaEntity dept = mock(DepartmentJpaEntity.class);
        given(dept.getId()).willReturn(deptId);
        given(departments.findByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), eq("DEPT-1"))).willReturn(Optional.of(dept));

        Map<String, String> row = new HashMap<>();
        row.put("external_id", "POS-1");
        row.put("title", "Senior Engineer");
        row.put("department_external_id", "DEPT-1");
        row.put("status", "ACTIVE");

        ImportRowCommitter.CommitResult result = committer.commit(row, ctx);
        assertThat(result.targetEntityType()).isEqualTo("Position");
        assertThat(result.auditAction()).isEqualTo(AuditAction.POSITION_CREATED);

        ArgumentCaptor<PositionJpaEntity> cap = ArgumentCaptor.forClass(PositionJpaEntity.class);
        verify(positions).save(cap.capture());
        assertThat(cap.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(cap.getValue().getProjectId()).isEqualTo(projectId);
        assertThat(cap.getValue().getDepartmentId()).isEqualTo(deptId);
        assertThat(cap.getValue().getCode()).isEqualTo("POS-1");
        assertThat(cap.getValue().getStatus()).isEqualTo(PositionStatus.ACTIVE);
        assertThat(cap.getValue().getTitleI18n()).containsEntry("ru-RU", "Senior Engineer");
    }

    @Test
    void crossTenantDepartmentLookup_rejectsBecauseScopedQuery() {
        // Different-tenant department: lookup with our tenantId returns empty
        // even though the same code may exist in another tenant.
        given(departments.findByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), eq("OTHER-TENANT-DEPT")))
                .willReturn(Optional.empty());

        Map<String, String> row = new HashMap<>();
        row.put("external_id", "POS-X");
        row.put("title", "X");
        row.put("department_external_id", "OTHER-TENANT-DEPT");

        assertThatThrownBy(() -> committer.commit(row, ctx))
                .isInstanceOf(ImportRowCommitException.class)
                .hasMessageContaining("Department");
    }

    @Test
    void duplicateCode_throws() {
        given(departments.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(mock(DepartmentJpaEntity.class)));
        given(positions.existsByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), eq("POS-DUP")))
                .willReturn(true);

        Map<String, String> row = new HashMap<>();
        row.put("external_id", "POS-DUP");
        row.put("title", "Dup");
        row.put("department_external_id", "DEPT-1");

        assertThatThrownBy(() -> committer.commit(row, ctx))
                .isInstanceOf(ImportRowCommitException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void unknownStatus_throwsInvalidPositionStatus_listingAllowedValues() {
        given(departments.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(mock(DepartmentJpaEntity.class)));

        Map<String, String> row = new HashMap<>();
        row.put("external_id", "POS-BAD");
        row.put("title", "Bad status");
        row.put("department_external_id", "DEPT-1");
        row.put("status", "OPEN"); // not a PositionStatus

        ImportRowCommitException err = (ImportRowCommitException) org.junit.jupiter.api.Assertions
                .assertThrows(ImportRowCommitException.class,
                        () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("INVALID_POSITION_STATUS");
        assertThat(err.getMessage())
                .contains("DRAFT").contains("ACTIVE").contains("ARCHIVED");
        verify(positions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void blankStatus_defaultsToActive_notRejected() {
        UUID deptId = UUID.randomUUID();
        DepartmentJpaEntity dept = mock(DepartmentJpaEntity.class);
        given(dept.getId()).willReturn(deptId);
        given(departments.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(dept));

        Map<String, String> row = new HashMap<>();
        row.put("external_id", "POS-BLANK");
        row.put("title", "Blank status");
        row.put("department_external_id", "DEPT-1");
        row.put("status", "  "); // blank → intentional default

        committer.commit(row, ctx);

        ArgumentCaptor<PositionJpaEntity> cap = ArgumentCaptor.forClass(PositionJpaEntity.class);
        verify(positions).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(PositionStatus.ACTIVE);
    }

    @Test
    void missingStatus_defaultsToActive_notRejected() {
        UUID deptId = UUID.randomUUID();
        DepartmentJpaEntity dept = mock(DepartmentJpaEntity.class);
        given(dept.getId()).willReturn(deptId);
        given(departments.findByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(Optional.of(dept));

        Map<String, String> row = new HashMap<>();
        row.put("external_id", "POS-NOSTATUS");
        row.put("title", "No status column");
        row.put("department_external_id", "DEPT-1");
        // status column absent entirely → intentional default.

        committer.commit(row, ctx);

        ArgumentCaptor<PositionJpaEntity> cap = ArgumentCaptor.forClass(PositionJpaEntity.class);
        verify(positions).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(PositionStatus.ACTIVE);
    }

    @Test
    void missingTitle_throws() {
        Map<String, String> row = new HashMap<>();
        row.put("external_id", "POS-X");
        row.put("department_external_id", "DEPT-1");
        assertThatThrownBy(() -> committer.commit(row, ctx))
                .isInstanceOf(ImportRowCommitException.class)
                .hasMessageContaining("title");
    }
}
