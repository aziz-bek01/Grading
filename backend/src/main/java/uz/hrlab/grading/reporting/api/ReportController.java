package uz.hrlab.grading.reporting.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.reporting.application.CancelReportUseCase;
import uz.hrlab.grading.reporting.application.IssueReportDownloadUrlUseCase;
import uz.hrlab.grading.reporting.application.ReportQueries;
import uz.hrlab.grading.reporting.application.RequestReportUseCase;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for the Report aggregate (architecture §17 / ADR-009).
 *
 * <p>Authorization: every endpoint is annotated with {@code @PreAuthorize} on
 * the matching permission code; use cases re-check the permission for defence
 * in depth. Path/query parameters NEVER include {@code tenantId} — sourced
 * from {@link uz.hrlab.grading.tenancy.application.TenantContext}.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final int MAX_PAGE_SIZE = 200;

    private final RequestReportUseCase requestUseCase;
    private final ReportQueries queries;
    private final IssueReportDownloadUrlUseCase downloadUseCase;
    private final CancelReportUseCase cancelUseCase;

    public ReportController(RequestReportUseCase requestUseCase,
                            ReportQueries queries,
                            IssueReportDownloadUrlUseCase downloadUseCase,
                            CancelReportUseCase cancelUseCase) {
        this.requestUseCase = requestUseCase;
        this.queries = queries;
        this.downloadUseCase = downloadUseCase;
        this.cancelUseCase = cancelUseCase;
    }

    @PostMapping("/request")
    @PreAuthorize("hasAuthority('REPORT_CREATE')")
    public ReportResponse request(@Valid @RequestBody RequestReportRequest req) {
        UUID id = requestUseCase.request(req.reportType(), req.format(),
                req.projectId(), req.filterParams());
        return ReportResponse.from(queries.get(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('REPORT_READ','REPORT_CREATE','REPORT_EXPORT')")
    public PageResponse<ReportResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportType type,
            @RequestParam(required = false) UUID requestedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, size));
        return PageResponse.of(queries.list(projectId, status, type, requestedBy, pageable),
                ReportResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('REPORT_READ','REPORT_CREATE','REPORT_EXPORT')")
    public ReportResponse get(@PathVariable("id") UUID id) {
        return ReportResponse.from(queries.get(id));
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public Map<String, String> download(@PathVariable("id") UUID id) {
        String url = downloadUseCase.issue(id);
        return Map.of("url", url);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('REPORT_CREATE','REPORT_EXPORT')")
    public ReportResponse cancel(@PathVariable("id") UUID id) {
        return ReportResponse.from(cancelUseCase.cancel(id));
    }
}
