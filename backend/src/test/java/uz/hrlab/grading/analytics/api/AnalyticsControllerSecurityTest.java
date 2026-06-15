package uz.hrlab.grading.analytics.api;

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
import uz.hrlab.grading.analytics.application.GetPortfolioSummaryQuery;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.api.GlobalExceptionHandler;
import uz.hrlab.grading.common.api.WebMvcSecurityTestConfig;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
@WebMvcTest(controllers = AnalyticsController.class,
        excludeAutoConfiguration = {
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "uz\\.hrlab\\.grading\\.security\\..*"))
@Import({WebMvcSecurityTestConfig.class, GlobalExceptionHandler.class})
class AnalyticsControllerSecurityTest {

    @Autowired MockMvc mvc;

    @MockBean GetPortfolioSummaryQuery portfolioSummary;
    // GlobalExceptionHandler constructor needs an AuditService bean (security
    // event logging); not exercised by these tests.
    @MockBean AuditService audit;

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/analytics/portfolio-summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutProjectReadIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/analytics/portfolio-summary")
                        .with(jwt().authorities(() -> "SALARY_VIEW")))
                .andExpect(status().isForbidden());
    }

    @Test
    void portfolioSummaryReturnsTenantScopedCountsInSnakeCase() throws Exception {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime activity = OffsetDateTime.parse("2026-06-15T10:15:30Z");
        given(portfolioSummary.summary()).willReturn(
                new PortfolioSummaryResponse(tenantId, 1L, 7L, 3L, activity));

        mvc.perform(get("/api/v1/analytics/portfolio-summary")
                        .with(jwt().authorities(() -> "PROJECT_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant_id").value(tenantId.toString()))
                .andExpect(jsonPath("$.client_company_count").value(1))
                .andExpect(jsonPath("$.project_count").value(7))
                .andExpect(jsonPath("$.methodology_count").value(3))
                .andExpect(jsonPath("$.last_activity_at").exists());
    }
}
