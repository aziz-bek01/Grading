package uz.hrlab.grading.analytics.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.analytics.application.GetPortfolioSummaryQuery;

/**
 * Read-only analytics endpoints for the dashboard.
 *
 * <p>Authorization: gated on {@code PROJECT_READ} (the broadest tenant-data read
 * permission). The tenant is sourced from {@code TenantContext} — NEVER a path
 * or query parameter — so the summary is always the caller's own tenant.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final GetPortfolioSummaryQuery portfolioSummary;

    public AnalyticsController(GetPortfolioSummaryQuery portfolioSummary) {
        this.portfolioSummary = portfolioSummary;
    }

    @GetMapping("/portfolio-summary")
    @PreAuthorize("hasAuthority('PROJECT_READ')")
    public PortfolioSummaryResponse portfolioSummary() {
        return portfolioSummary.summary();
    }
}
