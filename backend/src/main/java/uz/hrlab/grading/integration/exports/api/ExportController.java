package uz.hrlab.grading.integration.exports.api;

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
import uz.hrlab.grading.integration.exports.application.CancelExportJobUseCase;
import uz.hrlab.grading.integration.exports.application.ExportJobQueries;
import uz.hrlab.grading.integration.exports.application.IssueDownloadUrlUseCase;
import uz.hrlab.grading.integration.exports.application.RequestExportUseCase;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private static final int MAX_PAGE_SIZE = 200;

    private final RequestExportUseCase requestUseCase;
    private final ExportJobQueries queries;
    private final IssueDownloadUrlUseCase downloadUseCase;
    private final CancelExportJobUseCase cancelUseCase;

    public ExportController(RequestExportUseCase requestUseCase,
                            ExportJobQueries queries,
                            IssueDownloadUrlUseCase downloadUseCase,
                            CancelExportJobUseCase cancelUseCase) {
        this.requestUseCase = requestUseCase;
        this.queries = queries;
        this.downloadUseCase = downloadUseCase;
        this.cancelUseCase = cancelUseCase;
    }

    @PostMapping("/request")
    @PreAuthorize("isAuthenticated()")
    public ExportJobResponse request(@Valid @RequestBody RequestExportRequest req) {
        UUID id = requestUseCase.request(req.exportType(), req.format(), req.projectId(), req.filterParams());
        return ExportJobResponse.from(queries.get(id));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ExportJobResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) ExportJobStatus status,
            @RequestParam(required = false) ExportType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, size));
        return PageResponse.of(queries.list(projectId, status, type, pageable), ExportJobResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ExportJobResponse get(@PathVariable("id") UUID id) {
        return ExportJobResponse.from(queries.get(id));
    }

    @GetMapping("/{id}/download-url")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> download(@PathVariable("id") UUID id) {
        String url = downloadUseCase.issue(id);
        return Map.of("url", url);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ExportJobResponse cancel(@PathVariable("id") UUID id) {
        return ExportJobResponse.from(cancelUseCase.cancel(id));
    }
}
