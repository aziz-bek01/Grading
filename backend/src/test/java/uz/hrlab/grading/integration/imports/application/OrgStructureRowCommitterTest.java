package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.organization.domain.DepartmentStatus;
import uz.hrlab.grading.organization.domain.DepartmentType;
import uz.hrlab.grading.organization.infrastructure.DepartmentJpaEntity;
import uz.hrlab.grading.organization.infrastructure.DepartmentRepository;

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
 * PO-11 — unit tests for {@link OrgStructureRowCommitter}. Verifies row-level
 * commit DAO behaviour against a mocked {@link DepartmentRepository}:
 * <ol>
 *   <li>Valid root row (no parent) creates a Department and emits an
 *       {@code DEPARTMENT_CREATED} audit row.</li>
 *   <li>Row with parent_external_id resolves parent via tenant + project +
 *       code lookup.</li>
 *   <li>Missing required field → {@link ImportRowCommitException
 *       MISSING_REQUIRED_FIELD}.</li>
 *   <li>Parent code not present in this project → {@link
 *       ImportRowCommitException MISSING_PARENT} (cross-tenant safety:
 *       the lookup is tenant-scoped).</li>
 *   <li>Duplicate external_id in same project → {@link
 *       ImportRowCommitException DUPLICATE_CODE}.</li>
 * </ol>
 */
@Tag("imports")
class OrgStructureRowCommitterTest {

    private DepartmentRepository departments;
    private AuditService audit;
    private OrgStructureRowCommitter committer;

    private UUID tenantId;
    private UUID projectId;
    private ImportRowCommitContext ctx;

    @BeforeEach
    void setUp() {
        departments = mock(DepartmentRepository.class);
        audit = mock(AuditService.class);
        committer = new OrgStructureRowCommitter(departments, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ctx = new ImportRowCommitContext(tenantId, projectId, UUID.randomUUID(),
                UUID.randomUUID(), "trace-1");
        given(departments.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(departments.existsByTenantIdAndProjectIdAndCode(any(), any(), any()))
                .willReturn(false);
    }

    @Test
    void rootRow_createsDepartment_andEmitsAudit() {
        Map<String, String> row = new HashMap<>();
        row.put("external_id", "ROOT-1");
        row.put("name", "Headquarters");
        row.put("type", "DEPARTMENT");

        ImportRowCommitter.CommitResult result = committer.commit(row, ctx);
        assertThat(result.targetEntityType()).isEqualTo("Department");
        assertThat(result.auditAction()).isEqualTo(AuditAction.DEPARTMENT_CREATED);

        ArgumentCaptor<DepartmentJpaEntity> saved = ArgumentCaptor.forClass(DepartmentJpaEntity.class);
        verify(departments).save(saved.capture());
        DepartmentJpaEntity dept = saved.getValue();
        assertThat(dept.getTenantId()).isEqualTo(tenantId);
        assertThat(dept.getProjectId()).isEqualTo(projectId);
        assertThat(dept.getCode()).isEqualTo("ROOT-1");
        assertThat(dept.getParentId()).isNull();
        assertThat(dept.getStatus()).isEqualTo(DepartmentStatus.ACTIVE);
        assertThat(dept.getType()).isEqualTo(DepartmentType.DEPARTMENT);
        assertThat(dept.getNameI18n()).containsEntry("ru-RU", "Headquarters");

        ArgumentCaptor<AuditEvent> auditCap = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(auditCap.capture());
        assertThat(auditCap.getValue().action()).isEqualTo(AuditAction.DEPARTMENT_CREATED);
        assertThat(auditCap.getValue().tenantId()).isEqualTo(tenantId);
    }

    @Test
    void childRow_resolvesParentScopedByTenantAndProject() {
        UUID parentDeptId = UUID.randomUUID();
        DepartmentJpaEntity parentMock = mock(DepartmentJpaEntity.class);
        given(parentMock.getId()).willReturn(parentDeptId);
        given(departments.findByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), eq("ROOT-1")))
                .willReturn(Optional.of(parentMock));

        Map<String, String> row = new HashMap<>();
        row.put("external_id", "BR-1");
        row.put("name", "Branch 1");
        row.put("parent_external_id", "ROOT-1");
        row.put("type", "BRANCH");

        committer.commit(row, ctx);

        ArgumentCaptor<DepartmentJpaEntity> saved = ArgumentCaptor.forClass(DepartmentJpaEntity.class);
        verify(departments).save(saved.capture());
        assertThat(saved.getValue().getParentId()).isEqualTo(parentDeptId);
        assertThat(saved.getValue().getType()).isEqualTo(DepartmentType.BRANCH);
    }

    @Test
    void missingExternalId_throwsMissingRequiredField() {
        Map<String, String> row = new HashMap<>();
        row.put("name", "No id");
        assertThatThrownBy(() -> committer.commit(row, ctx))
                .isInstanceOf(ImportRowCommitException.class)
                .hasMessageContaining("external_id");
    }

    @Test
    void missingParent_throwsMissingParent() {
        given(departments.findByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), any())).willReturn(Optional.empty());
        Map<String, String> row = new HashMap<>();
        row.put("external_id", "BR-2");
        row.put("name", "Orphan");
        row.put("parent_external_id", "DOES-NOT-EXIST");

        assertThatThrownBy(() -> committer.commit(row, ctx))
                .isInstanceOf(ImportRowCommitException.class)
                .hasMessageContaining("Parent");
    }

    @Test
    void duplicateCode_throws() {
        given(departments.existsByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), eq("DUP")))
                .willReturn(true);
        Map<String, String> row = new HashMap<>();
        row.put("external_id", "DUP");
        row.put("name", "Duplicate");

        assertThatThrownBy(() -> committer.commit(row, ctx))
                .isInstanceOf(ImportRowCommitException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void noProjectInContext_throws() {
        ImportRowCommitContext noProject = new ImportRowCommitContext(
                tenantId, null, UUID.randomUUID(), UUID.randomUUID(), "t");
        Map<String, String> row = new HashMap<>();
        row.put("external_id", "X");
        row.put("name", "Y");
        assertThatThrownBy(() -> committer.commit(row, noProject))
                .isInstanceOf(ImportRowCommitException.class);
    }
}
