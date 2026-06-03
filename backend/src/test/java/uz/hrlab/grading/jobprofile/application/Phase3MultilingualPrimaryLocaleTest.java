package uz.hrlab.grading.jobprofile.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.AbacGate;
import uz.hrlab.grading.audit.application.AuditJsonRedactor;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatusTransitionPolicy;
import uz.hrlab.grading.jobprofile.domain.JobProfileTransitionRejectedException;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileJpaEntity;
import uz.hrlab.grading.jobprofile.infrastructure.JobProfileRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * D-302 — backend-side proof that submit-for-review enforces the primary
 * locale ({@code ru-RU}, architecture §20.3) on every long-text field.
 *
 * <p>Drives {@link SubmitJobProfileForReviewUseCase} with mocked
 * collaborators so the test runs without Testcontainers.
 */
@Tag("workflow")
class Phase3MultilingualPrimaryLocaleTest {

    private JobProfileRepository profiles;
    private PositionRepository positions;
    private AuditService audit;
    private AbacGate abacGate;
    private SubmitJobProfileForReviewUseCase useCase;

    private UUID profileId;
    private UUID tenantId;
    private UUID projectId;
    private UUID positionId;
    private UUID departmentId;

    @BeforeEach
    void setup() {
        profiles = mock(JobProfileRepository.class);
        positions = mock(PositionRepository.class);
        audit = mock(AuditService.class);
        abacGate = mock(AbacGate.class);
        var policy = new JobProfileStatusTransitionPolicy();
        var snapshot = new JobProfileAuditSnapshot(new AuditJsonRedactor(new ObjectMapper()));
        useCase = new SubmitJobProfileForReviewUseCase(profiles, positions, audit,
                abacGate, policy, snapshot,
                mock(uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase.class));

        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        positionId = UUID.randomUUID();
        departmentId = UUID.randomUUID();
        profileId = UUID.randomUUID();

        TenantContextHolder.set(new TenantContext(
                UUID.randomUUID(), tenantId,
                Set.of(projectId), Set.of("HRLAB_PROJECT_MANAGER"),
                Set.of("JOB_PROFILE_EDIT"), Set.of(), false, "ru-RU"));

        PositionJpaEntity position = mock(PositionJpaEntity.class);
        when(position.getId()).thenReturn(positionId);
        when(position.getProjectId()).thenReturn(projectId);
        when(position.getDepartmentId()).thenReturn(departmentId);
        when(positions.findByIdAndTenantId(positionId, tenantId))
                .thenReturn(Optional.of(position));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    private JobProfileJpaEntity newDraft() {
        return new JobProfileJpaEntity(profileId, tenantId, projectId, positionId,
                JobProfileStatus.DRAFT, 1, null);
    }

    @Test
    void rejectsSubmitWhenNoPrimaryLocaleAtAll() {
        JobProfileJpaEntity e = newDraft();
        // Only secondary locales — no ru-RU anywhere.
        e.setPurposeI18n(Map.of("en-US", "Purpose"));
        e.setMainDutiesI18n(Map.of("en-US", "Duties"));
        e.setResponsibilityAreaI18n(Map.of("en-US", "Area"));
        e.setKpiExpectedResultsI18n(Map.of("en-US", "KPI"));
        e.setEducationRequirementsI18n(Map.of("en-US", "Edu"));
        e.setExperienceRequirementsI18n(Map.of("en-US", "Exp"));
        when(profiles.findByIdAndTenantId(profileId, tenantId)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> useCase.submit(profileId))
                .isInstanceOf(JobProfileTransitionRejectedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(JobProfileTransitionRejectedException.class))
                .extracting(JobProfileTransitionRejectedException::getCode)
                .isEqualTo("PRIMARY_LOCALE_MISSING");
    }

    @Test
    void rejectsSubmitWhenPrimaryLocaleMissingOnSingleField() {
        JobProfileJpaEntity e = newDraft();
        e.setPurposeI18n(Map.of("ru-RU", "Цель"));
        // mainDuties has only en-US, ru-RU is missing.
        e.setMainDutiesI18n(Map.of("en-US", "Duties"));
        e.setResponsibilityAreaI18n(Map.of("ru-RU", "Зона"));
        e.setKpiExpectedResultsI18n(Map.of("ru-RU", "KPI"));
        e.setEducationRequirementsI18n(Map.of("ru-RU", "Образование"));
        e.setExperienceRequirementsI18n(Map.of("ru-RU", "Опыт"));
        when(profiles.findByIdAndTenantId(profileId, tenantId)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> useCase.submit(profileId))
                .isInstanceOf(JobProfileTransitionRejectedException.class)
                .hasMessageContaining("mainDutiesI18n");
    }

    @Test
    void rejectsSubmitWhenPrimaryLocaleValueIsBlank() {
        JobProfileJpaEntity e = newDraft();
        e.setPurposeI18n(Map.of("ru-RU", "  "));
        e.setMainDutiesI18n(Map.of("ru-RU", "Обязанности"));
        e.setResponsibilityAreaI18n(Map.of("ru-RU", "Зона"));
        e.setKpiExpectedResultsI18n(Map.of("ru-RU", "KPI"));
        e.setEducationRequirementsI18n(Map.of("ru-RU", "Образование"));
        e.setExperienceRequirementsI18n(Map.of("ru-RU", "Опыт"));
        when(profiles.findByIdAndTenantId(profileId, tenantId)).thenReturn(Optional.of(e));

        assertThatThrownBy(() -> useCase.submit(profileId))
                .isInstanceOf(JobProfileTransitionRejectedException.class)
                .hasMessageContaining("purposeI18n");
    }

    @Test
    void acceptsSubmitWhenAllSixRequiredFieldsHavePrimaryLocale() {
        JobProfileJpaEntity e = newDraft();
        e.setPurposeI18n(Map.of("ru-RU", "Цель"));
        e.setMainDutiesI18n(Map.of("ru-RU", "Обязанности"));
        e.setResponsibilityAreaI18n(Map.of("ru-RU", "Зона"));
        e.setKpiExpectedResultsI18n(Map.of("ru-RU", "KPI"));
        e.setEducationRequirementsI18n(Map.of("ru-RU", "Образование"));
        e.setExperienceRequirementsI18n(Map.of("ru-RU", "Опыт"));
        when(profiles.findByIdAndTenantId(profileId, tenantId)).thenReturn(Optional.of(e));
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var domain = useCase.submit(profileId);
        assertThat(domain.status()).isEqualTo(JobProfileStatus.UNDER_REVIEW);
    }

    @Test
    void primaryLocaleConstantIsRuRu() {
        assertThat(JobProfileLocales.PRIMARY_LOCALE).isEqualTo("ru-RU");
        assertThat(JobProfileLocales.ALL_LOCALES)
                .containsExactly("ru-RU", "uz-Cyrl-UZ", "uz-Latn-UZ", "en-US");
    }
}
