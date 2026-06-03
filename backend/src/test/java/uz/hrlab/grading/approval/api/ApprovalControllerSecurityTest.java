package uz.hrlab.grading.approval.api;

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
import uz.hrlab.grading.approval.application.ApprovalQueries;
import uz.hrlab.grading.approval.application.ApproveStepUseCase;
import uz.hrlab.grading.approval.application.CancelApprovalRequestUseCase;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.application.FindApprovalRequestByEntityQuery;
import uz.hrlab.grading.approval.application.ListMyPendingApprovalsQuery;
import uz.hrlab.grading.approval.application.RejectStepUseCase;
import uz.hrlab.grading.approval.application.RequestChangesUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = ApprovalController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class ApprovalControllerSecurityTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreateApprovalRequestUseCase createUseCase;
    @MockBean ApproveStepUseCase approveUseCase;
    @MockBean RejectStepUseCase rejectUseCase;
    @MockBean RequestChangesUseCase requestChangesUseCase;
    @MockBean CancelApprovalRequestUseCase cancelUseCase;
    @MockBean ListMyPendingApprovalsQuery inboxQuery;
    @MockBean FindApprovalRequestByEntityQuery findByEntity;
    @MockBean ApprovalRequestRepository requests;
    @MockBean ApprovalQueries queries;
    @MockBean AuditService audit;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/approval-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithoutPermissionIsForbidden() throws Exception {
        // @PreAuthorize fires before bean validation; supply a complete body so
        // we observe 403, not 400.
        String body = "{\"project_id\":\"" + UUID.randomUUID() + "\",\"entity_type\":\"JOB_PROFILE\","
                + "\"entity_id\":\"" + UUID.randomUUID() + "\",\"steps\":[{\"step_order\":1,"
                + "\"required_permission\":\"JOB_PROFILE_APPROVE\"}]}";
        mvc.perform(post("/api/v1/approval-requests")
                        .with(jwt().authorities(() -> "PROJECT_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveRequiresDecidePermission() throws Exception {
        mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/approve",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_CREATE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approveWithDecidePermissionSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        given(approveUseCase.approve(any(), any(), any())).willReturn(stubRequest(id));
        mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/approve", id, sid)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectWithDecidePermissionSucceeds() throws Exception {
        UUID id = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        given(rejectUseCase.reject(any(), any(), any())).willReturn(stubRequest(id));
        mvc.perform(post("/api/v1/approval-requests/{id}/steps/{sid}/reject", id, sid)
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"long enough reason yes truly here\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void inboxRequiresDecidePermission() throws Exception {
        mvc.perform(get("/api/v1/approval-requests/my-inbox")
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_CREATE")))
                .andExpect(status().isForbidden());
        given(inboxQuery.list()).willReturn(List.of());
        mvc.perform(get("/api/v1/approval-requests/my-inbox")
                        .with(jwt().authorities(() -> "APPROVAL_REQUEST_DECIDE")))
                .andExpect(status().isOk());
    }

    private ApprovalRequest stubRequest(UUID id) {
        return new ApprovalRequest(id, UUID.randomUUID(), UUID.randomUUID(),
                ApprovalEntityType.JOB_PROFILE, UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.now(), ApprovalRequestStatus.APPROVED, null, List.of());
    }
}
