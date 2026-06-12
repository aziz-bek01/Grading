package uz.hrlab.grading.evaluation.api;

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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.application.AssignEvaluatorUseCase;
import uz.hrlab.grading.evaluation.application.CreatePanelUseCase;
import uz.hrlab.grading.evaluation.application.LockRosterUseCase;
import uz.hrlab.grading.evaluation.application.PanelQueries;
import uz.hrlab.grading.evaluation.application.SubmitPanelToCeoUseCase;
import uz.hrlab.grading.evaluation.application.WithdrawEvaluatorUseCase;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluatorRole;
import uz.hrlab.grading.evaluation.domain.PanelAssignment;
import uz.hrlab.grading.evaluation.domain.PanelAssignmentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-16 — PanelController wire contract. Golden-file slice test pinning the
 * snake_case wire + permission gates for every new endpoint, and the
 * mass-assignment guard (server-computed fields are ignored on the create body).
 * Approve/reject/request-changes are NOT here (they go through the existing
 * ApprovalController for entity_type EVALUATION_PANEL).
 */
@Tag("security")
@WebMvcTest(controllers = {PanelController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class PanelControllerWireTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean CreatePanelUseCase createUseCase;
    @MockBean AssignEvaluatorUseCase assignUseCase;
    @MockBean WithdrawEvaluatorUseCase withdrawUseCase;
    @MockBean LockRosterUseCase lockRosterUseCase;
    @MockBean SubmitPanelToCeoUseCase submitUseCase;
    @MockBean PanelQueries queries;
    @MockBean uz.hrlab.grading.audit.application.AuditService auditService;

    // ----------------------------------------------------------- auth gates

    @Test
    void anonymousListIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/panels")).andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresPanelManage() throws Exception {
        mvc.perform(post("/api/v1/panels")
                        .with(jwt().authorities(() -> "EVALUATION_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRequiresEvaluationRead() throws Exception {
        mvc.perform(get("/api/v1/panels").with(jwt().authorities(() -> "ORG_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void resultRequiresCampaignResultsView() throws Exception {
        mvc.perform(get("/api/v1/panels/{id}/result", UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockRosterRequiresPanelManage() throws Exception {
        mvc.perform(post("/api/v1/panels/{id}/lock-roster", UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitRequiresPanelManage() throws Exception {
        mvc.perform(post("/api/v1/panels/{id}/submit", UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignRequiresPanelManage() throws Exception {
        mvc.perform(post("/api/v1/panels/{id}/evaluators", UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void withdrawRequiresPanelManage() throws Exception {
        mvc.perform(delete("/api/v1/panels/{id}/evaluators/{userId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isForbidden());
    }

    // ----------------------------------------------------------- wire shapes

    @Test
    void createReturns201WithSnakeCasePanelShape() throws Exception {
        UUID id = UUID.randomUUID();
        given(createUseCase.create(any())).willReturn(panel(id, EvaluationPanelStatus.COLLECTING));
        mvc.perform(post("/api/v1/panels")
                        .with(jwt().authorities(() -> "EVALUATION_PANEL_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.project_id").exists())
                .andExpect(jsonPath("$.position_id").exists())
                .andExpect(jsonPath("$.methodology_version_id").exists())
                .andExpect(jsonPath("$.status").value("COLLECTING"))
                .andExpect(jsonPath("$.min_evaluators").value(3))
                // NON_NULL: server-computed totals absent until AVERAGED.
                .andExpect(jsonPath("$.raw_total_score").doesNotExist())
                .andExpect(jsonPath("$.displayed_total_score").doesNotExist())
                .andExpect(jsonPath("$.assigned_grade_number").doesNotExist());
    }

    @Test
    void createIgnoresClientSuppliedServerComputedFields() throws Exception {
        // Mass-assignment guard (REQ-AVG-1): raw_total_score / evaluator_count /
        // assigned_grade_number in the body must be ignored — the response shows
        // the server's COLLECTING panel with null totals.
        UUID id = UUID.randomUUID();
        given(createUseCase.create(any())).willReturn(panel(id, EvaluationPanelStatus.COLLECTING));
        String body = """
                {
                  "position_id": "00000000-0000-0000-0000-000000000010",
                  "methodology_version_id": "00000000-0000-0000-0000-000000000020",
                  "raw_total_score": 999.9999,
                  "displayed_total_score": 999.99,
                  "evaluator_count": 99,
                  "assigned_grade_number": 16,
                  "tenant_id": "00000000-0000-0000-0000-000000000001"
                }
                """;
        mvc.perform(post("/api/v1/panels")
                        .with(jwt().authorities(() -> "EVALUATION_PANEL_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.raw_total_score").doesNotExist())
                .andExpect(jsonPath("$.assigned_grade_number").doesNotExist());
    }

    @Test
    void createRejectsMissingPositionId() throws Exception {
        mvc.perform(post("/api/v1/panels")
                        .with(jwt().authorities(() -> "EVALUATION_PANEL_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"methodology_version_id": "00000000-0000-0000-0000-000000000020"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignReturns201WithSnakeCaseAssignmentShape() throws Exception {
        UUID panelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(assignUseCase.assign(any(), any(), any()))
                .willReturn(assignment(panelId, userId, EvaluatorRole.EXTERNAL_EXPERT));
        mvc.perform(post("/api/v1/panels/{id}/evaluators", panelId)
                        .with(jwt().authorities(() -> "EVALUATION_PANEL_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.panel_id").value(panelId.toString()))
                .andExpect(jsonPath("$.evaluator_user_id").value(userId.toString()))
                .andExpect(jsonPath("$.evaluator_role").value("EXTERNAL_EXPERT"))
                .andExpect(jsonPath("$.assignment_status").value("ASSIGNED"));
    }

    @Test
    void assignAcceptsSnakeCaseRequestFields() throws Exception {
        UUID panelId = UUID.randomUUID();
        given(assignUseCase.assign(any(), any(), any()))
                .willReturn(assignment(panelId, UUID.randomUUID(), EvaluatorRole.HR_DIRECTOR));
        mvc.perform(post("/api/v1/panels/{id}/evaluators", panelId)
                        .with(jwt().authorities(() -> "EVALUATION_PANEL_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evaluator_user_id": "00000000-0000-0000-0000-000000000030",
                                  "evaluator_role": "HR_DIRECTOR"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void withdrawReturns204() throws Exception {
        given(withdrawUseCase.withdraw(any(), any()))
                .willReturn(assignment(UUID.randomUUID(), UUID.randomUUID(), EvaluatorRole.ADDITIONAL));
        mvc.perform(delete("/api/v1/panels/{id}/evaluators/{userId}",
                        UUID.randomUUID(), UUID.randomUUID())
                        .with(jwt().authorities(() -> "EVALUATION_PANEL_MANAGE")))
                .andExpect(status().isNoContent());
    }

    @Test
    void listReturnsPageResponseWithItemsKey() throws Exception {
        given(queries.list(any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(
                        PanelResponse.from(panel(UUID.randomUUID(), EvaluationPanelStatus.AVERAGED),
                                Map.of("ru-RU", "Кассир"), 3, 3))));
        mvc.perform(get("/api/v1/panels").with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].status").value("AVERAGED"))
                .andExpect(jsonPath("$.items[0].evaluator_count").value(3))
                .andExpect(jsonPath("$.items[0].completed_count").value(3))
                .andExpect(jsonPath("$.items[0].position_title_i18n['ru-RU']").value("Кассир"));
    }

    @Test
    void detailReturnsSnakeCasePanelAndRoster() throws Exception {
        UUID panelId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PanelResponse summary = PanelResponse.from(
                panel(panelId, EvaluationPanelStatus.AWAITING_EVALUATIONS), null, 3, 1);
        PanelAssignmentResponse seat = PanelAssignmentResponse.from(
                assignment(panelId, userId, EvaluatorRole.DEPARTMENT_DIRECTOR), "Ali Valiyev");
        given(queries.getPanelDetail(panelId))
                .willReturn(new PanelDetailResponse(summary, List.of(seat), 1, 3));
        mvc.perform(get("/api/v1/panels/{id}", panelId)
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panel.status").value("AWAITING_EVALUATIONS"))
                .andExpect(jsonPath("$.completed_count").value(1))
                .andExpect(jsonPath("$.total_count").value(3))
                .andExpect(jsonPath("$.roster[0].evaluator_name").value("Ali Valiyev"))
                .andExpect(jsonPath("$.roster[0].evaluator_role").value("DEPARTMENT_DIRECTOR"));
    }

    @Test
    void resultReturnsGatedAveragedShapeForResultViewers() throws Exception {
        UUID panelId = UUID.randomUUID();
        UUID factorId = UUID.randomUUID();
        PanelResultResponse result = new PanelResultResponse(
                panelId, new BigDecimal("20.00"), new BigDecimal("20.0000"), 3,
                List.of(new PanelResultResponse.FactorAverage(
                        factorId, Map.of("ru-RU", "Знания"),
                        new BigDecimal("15.0000"), 3, List.of(), new BigDecimal("4.0000"))),
                UUID.randomUUID(), 12);
        given(queries.getResult(panelId)).willReturn(result);
        mvc.perform(get("/api/v1/panels/{id}/result", panelId)
                        .with(jwt().authorities(() -> "CAMPAIGN_RESULTS_VIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.panel_id").value(panelId.toString()))
                .andExpect(jsonPath("$.displayed_total_score").value(20.00))
                .andExpect(jsonPath("$.evaluator_count").value(3))
                .andExpect(jsonPath("$.assigned_grade_number").value(12))
                .andExpect(jsonPath("$.factor_averages[0].factor_id").value(factorId.toString()))
                .andExpect(jsonPath("$.factor_averages[0].avg_raw_factor_score").value(15.0000))
                .andExpect(jsonPath("$.factor_averages[0].disagreement_range").value(4.0000));
    }

    @Test
    void resultWhileCollectingIs404() throws Exception {
        UUID panelId = UUID.randomUUID();
        given(queries.getResult(panelId)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/panels/{id}/result", panelId)
                        .with(jwt().authorities(() -> "CAMPAIGN_RESULTS_VIEW")))
                .andExpect(status().isNotFound());
    }

    @Test
    void resultWithoutResultsViewInsideQueryIs403() throws Exception {
        // Even if @PreAuthorize were satisfied, the query enforces the gate again.
        UUID panelId = UUID.randomUUID();
        given(queries.getResult(panelId)).willThrow(new PermissionDeniedException());
        mvc.perform(get("/api/v1/panels/{id}/result", panelId)
                        .with(jwt().authorities(() -> "CAMPAIGN_RESULTS_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownPanelIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(queries.getPanelDetail(id)).willThrow(new TenantAccessDeniedException());
        mvc.perform(get("/api/v1/panels/{id}", id)
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------- helpers

    private EvaluationPanel panel(UUID id, EvaluationPanelStatus status) {
        return new EvaluationPanel(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), status, 3, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                OffsetDateTime.now());
    }

    private PanelAssignment assignment(UUID panelId, UUID userId, EvaluatorRole role) {
        return new PanelAssignment(UUID.randomUUID(), UUID.randomUUID(), panelId, userId, role,
                null, PanelAssignmentStatus.ASSIGNED, OffsetDateTime.now());
    }

    private String createBody() {
        return """
                {
                  "position_id": "00000000-0000-0000-0000-000000000010",
                  "methodology_version_id": "00000000-0000-0000-0000-000000000020"
                }
                """;
    }

    private String assignBody() {
        return """
                {
                  "evaluator_user_id": "00000000-0000-0000-0000-000000000030",
                  "evaluator_role": "EXTERNAL_EXPERT"
                }
                """;
    }
}
