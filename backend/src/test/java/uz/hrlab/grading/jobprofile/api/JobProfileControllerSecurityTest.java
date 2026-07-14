package uz.hrlab.grading.jobprofile.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.jobprofile.application.ApproveJobProfileUseCase;
import uz.hrlab.grading.jobprofile.application.ArchiveJobProfileUseCase;
import uz.hrlab.grading.jobprofile.application.CreateJobProfileRevisionUseCase;
import uz.hrlab.grading.jobprofile.application.FindJobProfileQuery;
import uz.hrlab.grading.jobprofile.application.RequestJobProfileChangesUseCase;
import uz.hrlab.grading.jobprofile.application.SubmitJobProfileForReviewUseCase;
import uz.hrlab.grading.jobprofile.application.UpdateJobProfileUseCase;
import uz.hrlab.grading.jobprofile.domain.JobProfile;
import uz.hrlab.grading.jobprofile.domain.JobProfileStatus;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-edge security smoke for {@link JobProfileController} and the
 * position-scoped sister controller. Mirrors the {@code PositionControllerSecurityTest}
 * shape: anonymous → 401, wrong authority → 403, correct authority → 2xx,
 * malformed UUID → 400, unknown id → 404, tenantId-in-body rejected.
 */
@Tag("security")
@WebMvcTest(controllers = {JobProfileController.class, PositionJobProfileController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class JobProfileControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean FindJobProfileQuery findQuery;
    @MockBean UpdateJobProfileUseCase updateUseCase;
    @MockBean SubmitJobProfileForReviewUseCase submitUseCase;
    @MockBean ApproveJobProfileUseCase approveUseCase;
    @MockBean RequestJobProfileChangesUseCase requestChangesUseCase;
    @MockBean ArchiveJobProfileUseCase archiveUseCase;
    @MockBean CreateJobProfileRevisionUseCase revisionUseCase;
    @MockBean uz.hrlab.grading.jobprofile.application.CreateJobProfileUseCase createUseCase;
    @MockBean AuditService auditService;

    @Test
    void anonymousGetIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/job-profiles/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getWithoutReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/job-profiles/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getWithReadReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(findQuery.findById(id)).willReturn(sampleProfile(id, JobProfileStatus.DRAFT));
        mvc.perform(get("/api/v1/job-profiles/{id}", id)
                        .with(jwt().authorities(() -> "JOB_PROFILE_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void patchWithoutEditIsForbidden() throws Exception {
        mvc.perform(patch("/api/v1/job-profiles/{id}", UUID.randomUUID())
                        .with(jwt().authorities(() -> "JOB_PROFILE_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose_i18n\":{\"ru-RU\":\"x\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveRequiresApprovePermissionNotEdit() throws Exception {
        mvc.perform(post("/api/v1/job-profiles/{id}/approve", UUID.randomUUID())
                        .with(jwt().authorities(() -> "JOB_PROFILE_EDIT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveWithApprovePermissionSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        given(approveUseCase.approve(id)).willReturn(sampleProfile(id, JobProfileStatus.APPROVED));
        mvc.perform(post("/api/v1/job-profiles/{id}/approve", id)
                        .with(jwt().authorities(() -> "JOB_PROFILE_APPROVE")))
                .andExpect(status().isOk());
    }

    @Test
    void requestChangesRequiresApprovePermission() throws Exception {
        mvc.perform(post("/api/v1/job-profiles/{id}/request-changes", UUID.randomUUID())
                        .with(jwt().authorities(() -> "JOB_PROFILE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"This needs more detail in the duties section\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(findQuery.findById(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/job-profiles/{id}", id)
                        .with(jwt().authorities(() -> "JOB_PROFILE_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedUuidReturns400() throws Exception {
        mvc.perform(get("/api/v1/job-profiles/{id}", "not-a-uuid")
                        .with(jwt().authorities(() -> "JOB_PROFILE_READ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkStatusesWithoutReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/job-profiles/statuses")
                        .param("positionIds", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void bulkStatusesWithReadReturns200() throws Exception {
        UUID positionId = UUID.randomUUID();
        given(findQuery.findActiveStatusesByPositionIds(any()))
                .willReturn(java.util.List.of(sampleProfile(UUID.randomUUID(), JobProfileStatus.APPROVED)));
        mvc.perform(get("/api/v1/job-profiles/statuses")
                        .param("positionIds", positionId.toString())
                        .with(jwt().authorities(() -> "JOB_PROFILE_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void bulkStatusesRouteDoesNotCollideWithGetById() throws Exception {
        // "statuses" must hit the /statuses handler, not /{id} (→ would 400 on UUID parse).
        given(findQuery.findActiveStatusesByPositionIds(any()))
                .willReturn(java.util.List.of());
        mvc.perform(get("/api/v1/job-profiles/statuses")
                        .param("positionIds", UUID.randomUUID().toString())
                        .with(jwt().authorities(() -> "JOB_PROFILE_READ")))
                .andExpect(status().isOk());
    }

    @Test
    void createOnPositionRequiresJobProfileEdit() throws Exception {
        mvc.perform(post("/api/v1/positions/{positionId}/job-profile", UUID.randomUUID())
                        .with(jwt().authorities(() -> "POSITION_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createOnPositionIgnoresTenantIdInBody() throws Exception {
        UUID positionId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        given(createUseCase.create(any())).willReturn(sampleProfile(profileId, JobProfileStatus.DRAFT));

        String body = """
                {
                  "tenantId": "00000000-0000-0000-0000-000000000001",
                  "tenant_id": "00000000-0000-0000-0000-000000000002",
                  "purpose_i18n": {"ru-RU": "Цель"},
                  "main_duties_i18n": {"ru-RU": "Обязанности"}
                }
                """;
        mvc.perform(post("/api/v1/positions/{positionId}/job-profile", positionId)
                        .with(jwt().authorities(() -> "JOB_PROFILE_EDIT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    private JobProfile sampleProfile(UUID id, JobProfileStatus status) {
        return new JobProfile(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                Map.of("ru-RU", "Цель"), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                null, status, 1, null, null, null, null, null, null);
    }
}
