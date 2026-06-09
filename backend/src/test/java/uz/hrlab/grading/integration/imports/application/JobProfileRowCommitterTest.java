package uz.hrlab.grading.integration.imports.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.jobprofile.application.CreateJobProfileCommand;
import uz.hrlab.grading.jobprofile.application.JobProfileLocales;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.application.ProjectLockedException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PO-11 — offline unit tests for {@link JobProfileRowCommitter}. The committer
 * is a thin adapter: it resolves {@code position_external_id} → Position id and
 * delegates to {@link JobProfileImportGateway} (mocked here), which in
 * production wraps the reused {@code CreateJobProfileUseCase} in a
 * REQUIRES_NEW boundary. Verifies:
 * <ol>
 *   <li>Happy path: resolves the position, builds a command with the mapped
 *       i18n fields (purpose→purposeI18n, responsibilities→mainDutiesI18n,
 *       requirements→knowledgeSkillsI18n under ru-RU) and returns a
 *       JobProfile / JOB_PROFILE_CREATED CommitResult.</li>
 *   <li>Position lookup is tenant- + project-scoped → unknown code →
 *       MISSING_POSITION (use-case never called).</li>
 *   <li>Blank purpose → MISSING_REQUIRED_FIELD (use-case never called).</li>
 *   <li>Use-case PROFILE_ALREADY_EXISTS → translated to row error.</li>
 *   <li>Project locked / ABAC denial → translated to clear row codes.</li>
 * </ol>
 */
@Tag("imports")
class JobProfileRowCommitterTest {

    private PositionRepository positions;
    private JobProfileImportGateway gateway;
    private JobProfileRowCommitter committer;

    private UUID tenantId;
    private UUID projectId;
    private UUID positionId;
    private ImportRowCommitContext ctx;

    @BeforeEach
    void setUp() {
        positions = mock(PositionRepository.class);
        gateway = mock(JobProfileImportGateway.class);
        committer = new JobProfileRowCommitter(positions, gateway);

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        ctx = new ImportRowCommitContext(tenantId, projectId, UUID.randomUUID(),
                UUID.randomUUID(), "trace-jp");
    }

    private void givenPositionResolves() {
        PositionJpaEntity position = mock(PositionJpaEntity.class);
        given(position.getId()).willReturn(positionId);
        given(positions.findByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), eq("POS-CFO")))
                .willReturn(Optional.of(position));
    }

    @Test
    void validRow_createsDraftProfile_andMapsColumnsToI18nFields() {
        givenPositionResolves();
        UUID createdId = UUID.randomUUID();
        JobProfile created = mock(JobProfile.class);
        given(created.id()).willReturn(createdId);
        given(gateway.create(any())).willReturn(created);

        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "POS-CFO");
        row.put("purpose", "Lead financial strategy");
        row.put("responsibilities", "Own budgeting; lead audits");
        row.put("requirements", "IFRS; 10+ years finance");

        ImportRowCommitter.CommitResult result = committer.commit(row, ctx);

        assertThat(result.targetEntityType()).isEqualTo("JobProfile");
        assertThat(result.entityId()).isEqualTo(createdId);
        assertThat(result.auditAction()).isEqualTo(AuditAction.JOB_PROFILE_CREATED);

        // Verify the command passed to the reused use-case carries the mapping.
        ArgumentCaptor<CreateJobProfileCommand> cap =
                ArgumentCaptor.forClass(CreateJobProfileCommand.class);
        verify(gateway).create(cap.capture());
        CreateJobProfileCommand cmd = cap.getValue();
        assertThat(cmd.positionId()).isEqualTo(positionId);
        assertThat(cmd.purposeI18n())
                .containsEntry(JobProfileLocales.PRIMARY_LOCALE, "Lead financial strategy");
        assertThat(cmd.mainDutiesI18n())
                .containsEntry(JobProfileLocales.PRIMARY_LOCALE, "Own budgeting; lead audits");
        assertThat(cmd.knowledgeSkillsI18n())
                .containsEntry(JobProfileLocales.PRIMARY_LOCALE, "IFRS; 10+ years finance");
        // Other rich fields stay empty (DRAFT allows partial) and date is null.
        assertThat(cmd.responsibilityAreaI18n()).isNull();
        assertThat(cmd.actualizationDate()).isNull();
    }

    @Test
    void blankOptionalColumns_yieldNullI18nMaps_andStillCreatesDraft() {
        givenPositionResolves();
        JobProfile created = mock(JobProfile.class);
        given(created.id()).willReturn(UUID.randomUUID());
        given(gateway.create(any())).willReturn(created);

        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "POS-CFO");
        row.put("purpose", "Lead financial strategy");
        // responsibilities + requirements absent / blank.
        row.put("responsibilities", "   ");

        committer.commit(row, ctx);

        ArgumentCaptor<CreateJobProfileCommand> cap =
                ArgumentCaptor.forClass(CreateJobProfileCommand.class);
        verify(gateway).create(cap.capture());
        assertThat(cap.getValue().mainDutiesI18n()).isNull();
        assertThat(cap.getValue().knowledgeSkillsI18n()).isNull();
        assertThat(cap.getValue().purposeI18n())
                .containsEntry(JobProfileLocales.PRIMARY_LOCALE, "Lead financial strategy");
    }

    @Test
    void unknownPosition_throwsMissingPosition_andDoesNotCallUseCase() {
        given(positions.findByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), eq("POS-GHOST")))
                .willReturn(Optional.empty());

        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "POS-GHOST");
        row.put("purpose", "Some purpose");

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("MISSING_POSITION");
        assertThat(err.getMessage()).contains("POS-GHOST");
        verify(gateway, never()).create(any());
    }

    @Test
    void crossTenantPositionLookup_rejectsBecauseScopedQuery() {
        // The same code may exist in another tenant, but the scoped lookup with
        // our tenantId returns empty.
        given(positions.findByTenantIdAndProjectIdAndCode(
                eq(tenantId), eq(projectId), eq("OTHER-TENANT-POS")))
                .willReturn(Optional.empty());

        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "OTHER-TENANT-POS");
        row.put("purpose", "Some purpose");

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("MISSING_POSITION");
        verify(gateway, never()).create(any());
    }

    @Test
    void blankPurpose_throwsMissingRequiredField_andDoesNotCallUseCase() {
        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "POS-CFO");
        row.put("purpose", "   ");

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("MISSING_REQUIRED_FIELD");
        assertThat(err.getMessage()).contains("purpose");
        verify(gateway, never()).create(any());
    }

    @Test
    void missingPositionExternalId_throwsMissingRequiredField() {
        Map<String, String> row = new HashMap<>();
        row.put("purpose", "Some purpose");

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("MISSING_REQUIRED_FIELD");
        assertThat(err.getMessage()).contains("position_external_id");
        verify(gateway, never()).create(any());
    }

    @Test
    void duplicateActiveProfile_translatesToProfileAlreadyExists() {
        givenPositionResolves();
        given(gateway.create(any()))
                .willThrow(new ValidationException("PROFILE_ALREADY_EXISTS",
                        "An active profile already exists for this position"));

        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "POS-CFO");
        row.put("purpose", "Some purpose");

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("PROFILE_ALREADY_EXISTS");
        assertThat(err.getMessage()).contains("POS-CFO");
    }

    @Test
    void projectLocked_translatesToProjectLocked() {
        givenPositionResolves();
        given(gateway.create(any())).willThrow(new ProjectLockedException());

        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "POS-CFO");
        row.put("purpose", "Some purpose");

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("PROJECT_LOCKED");
    }

    @Test
    void abacDenial_translatesToAccessDenied() {
        givenPositionResolves();
        given(gateway.create(any())).willThrow(new TenantAccessDeniedException());

        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "POS-CFO");
        row.put("purpose", "Some purpose");

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, ctx));
        assertThat(err.getCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void nullProject_throwsProjectRequired() {
        ImportRowCommitContext noProject = new ImportRowCommitContext(
                tenantId, null, UUID.randomUUID(), UUID.randomUUID(), "trace-jp");

        Map<String, String> row = new HashMap<>();
        row.put("position_external_id", "POS-CFO");
        row.put("purpose", "Some purpose");

        ImportRowCommitException err = assertThrows(ImportRowCommitException.class,
                () -> committer.commit(row, noProject));
        assertThat(err.getCode()).isEqualTo("PROJECT_REQUIRED");
    }
}
