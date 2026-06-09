package uz.hrlab.grading.integration.imports.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.imports.domain.ImportTemplateCode;
import uz.hrlab.grading.jobprofile.application.CreateJobProfileCommand;
import uz.hrlab.grading.jobprofile.application.JobProfileLocales;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.project.application.ProjectLockedException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PO-11 — JOB_PROFILE_V1 commit DAO. Each parsed row materializes one DRAFT
 * {@link JobProfile} (version 1) for the position referenced by
 * {@code position_external_id}.
 *
 * <p><b>Reuse over re-implementation:</b> this committer is a thin adapter. It
 * only (1) resolves {@code position_external_id} → a Position id scoped to the
 * authenticated tenant + project and (2) maps the 4 flat template columns to
 * the rich i18n domain fields. All real work — position load, ABAC
 * (project + department write), project LOCKED/ARCHIVED guard, the
 * one-active-profile-per-position uniqueness rule, entity creation and the
 * {@code JOB_PROFILE_CREATED} audit — is delegated to
 * {@code CreateJobProfileUseCase#create} via {@link JobProfileImportGateway}.
 * It reads the active {@link uz.hrlab.grading.tenancy.application.TenantContext}
 * which the import commit has already established (same thread / same context as
 * the other committers).
 *
 * <p>The call goes through {@link JobProfileImportGateway} (a
 * {@code REQUIRES_NEW} boundary) so a single row rejection rolls back only that
 * row and never marks the shared batch transaction rollback-only — preserving
 * the "other rows continue" contract of {@code CommitImportBatchUseCase}.
 *
 * <p><b>Column → domain-field mapping</b> (MVP: the flat template carries only
 * three free-text columns + the FK column; the remaining rich i18n fields stay
 * empty, which a DRAFT permits):
 * <ul>
 *   <li>{@code position_external_id} → Position FK (resolved, not stored)</li>
 *   <li>{@code purpose}          → {@code purposeI18n} (required)</li>
 *   <li>{@code responsibilities} → {@code mainDutiesI18n}</li>
 *   <li>{@code requirements}     → {@code knowledgeSkillsI18n}</li>
 * </ul>
 * Each non-blank value is wrapped under the primary locale
 * ({@link JobProfileLocales#PRIMARY_LOCALE} = {@code ru-RU}) — consistent with
 * how {@code OrgStructureRowCommitter} / {@code PositionCatalogRowCommitter}
 * seed their i18n maps.
 *
 * <p><b>Import order:</b> the referenced position MUST already exist (import
 * the Position Catalog before Job Profiles). An unknown
 * {@code position_external_id} fails that single row with MISSING_POSITION; the
 * rest of the batch continues.
 */
@Component
public class JobProfileRowCommitter implements ImportRowCommitter {

    private final PositionRepository positions;
    private final JobProfileImportGateway gateway;

    public JobProfileRowCommitter(PositionRepository positions,
                                  JobProfileImportGateway gateway) {
        this.positions = positions;
        this.gateway = gateway;
    }

    @Override
    public String templateCode() {
        return ImportTemplateCode.JOB_PROFILE_V1;
    }

    @Override
    public CommitResult commit(Map<String, String> row, ImportRowCommitContext ctx) {
        if (ctx.projectId() == null) {
            throw new ImportRowCommitException("PROJECT_REQUIRED",
                    "JOB_PROFILE_V1 commit requires a project");
        }

        String positionExternalId = req(row, "position_external_id");
        String purpose = req(row, "purpose"); // required by template + domain

        // Resolve FK first so a clear MISSING_POSITION beats any downstream
        // 404. The lookup is tenant- + project-scoped → cross-tenant codes
        // cannot resolve.
        PositionJpaEntity position = positions
                .findByTenantIdAndProjectIdAndCode(ctx.tenantId(), ctx.projectId(),
                        positionExternalId)
                .orElseThrow(() -> new ImportRowCommitException("MISSING_POSITION",
                        "Position with external_id '" + positionExternalId
                                + "' was not found in this project. Import positions first."));

        CreateJobProfileCommand cmd = new CreateJobProfileCommand(
                position.getId(),
                i18n(purpose),                       // purpose → purposeI18n
                i18n(row.get("responsibilities")),   // responsibilities → mainDutiesI18n
                null,                                 // responsibilityAreaI18n
                null,                                 // authorityI18n
                null,                                 // kpiExpectedResultsI18n
                null,                                 // educationRequirementsI18n
                null,                                 // experienceRequirementsI18n
                i18n(row.get("requirements")),       // requirements → knowledgeSkillsI18n
                null,                                 // internalInteractionsI18n
                null,                                 // externalInteractionsI18n
                null,                                 // workingConditionsI18n
                null,                                 // documentsRegulationsI18n
                null);                                // actualizationDate

        UUID createdId;
        try {
            JobProfile created = gateway.create(cmd);
            createdId = created.id();
        } catch (ValidationException e) {
            // The only ValidationException the create use-case raises is the
            // one-active-profile-per-position guard — surface it as a stable
            // row code so the row fails gracefully and others continue.
            throw new ImportRowCommitException("PROFILE_ALREADY_EXISTS",
                    "An active job profile already exists for position '"
                            + positionExternalId + "'");
        } catch (ProjectLockedException e) {
            throw new ImportRowCommitException("PROJECT_LOCKED",
                    "Project is locked or archived; job profiles cannot be created");
        } catch (TenantAccessDeniedException e) {
            // Raised by the use-case when the position lookup OR an ABAC
            // (project / department write) policy denies. We already resolved
            // the position above, so reaching here means an ABAC denial.
            throw new ImportRowCommitException("ACCESS_DENIED",
                    "Not allowed to create a job profile for position '"
                            + positionExternalId + "' in this project/department");
        }

        return new CommitResult("JobProfile", createdId, AuditAction.JOB_PROFILE_CREATED);
    }

    private static String req(Map<String, String> row, String key) {
        String v = row.get(key);
        if (v == null || v.isBlank()) {
            throw new ImportRowCommitException("MISSING_REQUIRED_FIELD",
                    "Required field missing: " + key);
        }
        return v.trim();
    }

    /**
     * Wrap a flat free-text value into a single-locale i18n map under the
     * primary locale, or return {@code null} when blank (a DRAFT allows the
     * rich fields to be empty).
     */
    private static Map<String, String> i18n(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        map.put(JobProfileLocales.PRIMARY_LOCALE, value.trim());
        return map;
    }
}
