package uz.hrlab.grading.evaluation.api;

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
import org.springframework.test.web.servlet.MockMvc;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;
import uz.hrlab.grading.evaluation.migration.PanelApprovalReconciliationRunner;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BE-044 — {@link PanelOpsController} wire contract. Pins that the ops-repair
 * {@code POST /api/v1/panels/reconcile-approvals} route stays where the frontend
 * expects it ({@code endpoints.panels.reconcileApprovals}) and keeps its
 * {@code EVALUATION_PANEL_MANAGE} gate after moving OFF the panel CRUD controller.
 */
@Tag("security")
@WebMvcTest(controllers = {PanelOpsController.class},
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class PanelOpsControllerWireTest {

    @Autowired MockMvc mvc;

    @MockBean PanelApprovalReconciliationRunner reconciliationRunner;
    @MockBean uz.hrlab.grading.audit.application.AuditService auditService;

    @Test
    void reconcileApprovalsRejectsAnonymous() throws Exception {
        mvc.perform(post("/api/v1/panels/reconcile-approvals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reconcileApprovalsRequiresPanelManage() throws Exception {
        // Wrong authority — route is wired to PanelOpsController and gated on
        // EVALUATION_PANEL_MANAGE (403, not 404 — proving the move kept the path).
        mvc.perform(post("/api/v1/panels/reconcile-approvals")
                        .with(jwt().authorities(() -> "EVALUATION_READ")))
                .andExpect(status().isForbidden());
    }
}
