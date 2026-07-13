package uz.hrlab.grading.integration.exports.api;

import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.common.api.Pagination;
import uz.hrlab.grading.integration.exports.application.CancelExportJobUseCase;
import uz.hrlab.grading.integration.exports.application.DownloadExportFileUseCase;
import uz.hrlab.grading.integration.exports.application.ExportJobQueries;
import uz.hrlab.grading.integration.exports.application.IssueDownloadUrlUseCase;
import uz.hrlab.grading.integration.exports.application.RequestExportUseCase;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exports")
public class ExportController {

    private final RequestExportUseCase requestUseCase;
    private final ExportJobQueries queries;
    private final IssueDownloadUrlUseCase downloadUseCase;
    private final DownloadExportFileUseCase downloadFileUseCase;
    private final CancelExportJobUseCase cancelUseCase;

    public ExportController(RequestExportUseCase requestUseCase,
                            ExportJobQueries queries,
                            IssueDownloadUrlUseCase downloadUseCase,
                            DownloadExportFileUseCase downloadFileUseCase,
                            CancelExportJobUseCase cancelUseCase) {
        this.requestUseCase = requestUseCase;
        this.queries = queries;
        this.downloadUseCase = downloadUseCase;
        this.downloadFileUseCase = downloadFileUseCase;
        this.cancelUseCase = cancelUseCase;
    }

    // Controller-level backstop (F2, defense-in-depth) mirrors the type-dependent
    // permissions the use case enforces via ExportTypePermissions#requiredPermission
    // (salary types→SALARY_EXPORT, executive report→REPORT_EXPORT, everything
    // else→EXPORT_REQUEST). The exact per-type check still runs in
    // RequestExportUseCase — salary-bearing exports are re-gated on SALARY_EXPORT.
    @PostMapping("/request")
    @PreAuthorize("hasAnyAuthority('EXPORT_REQUEST','REPORT_EXPORT','SALARY_EXPORT')")
    public ExportJobResponse request(@Valid @RequestBody RequestExportRequest req) {
        UUID id = requestUseCase.request(req.exportType(), req.format(), req.projectId(), req.filterParams());
        return queries.get(id);
    }

    // Read backstop (F2) mirrors ExportJobQueries#requireRead.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('EXPORT_READ','EXPORT_REQUEST','REPORT_EXPORT','SALARY_EXPORT')")
    public PageResponse<ExportJobResponse> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) ExportJobStatus status,
            @RequestParam(required = false) ExportType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = Pagination.of(page, size);
        return PageResponse.from(queries.list(projectId, status, type, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('EXPORT_READ','EXPORT_REQUEST','REPORT_EXPORT','SALARY_EXPORT')")
    public ExportJobResponse get(@PathVariable("id") UUID id) {
        return queries.get(id);
    }

    /**
     * Resolve the same-origin, authenticated download URL. Returns a RELATIVE
     * path ({@code /api/v1/exports/{id}/download}) — the SPA fetches it as an
     * authenticated blob (JWT header). No {@code file://} URL, no signed token in
     * the URL. Salary-bearing exports are re-checked for {@code SALARY_EXPORT}
     * inside the use case.
     */
    // Backstop (F2) mirrors the type-dependent permission re-checked by
    // IssueDownloadUrlUseCase via ExportTypePermissions#requiredPermission.
    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAnyAuthority('EXPORT_REQUEST','REPORT_EXPORT','SALARY_EXPORT')")
    public Map<String, String> downloadUrl(@PathVariable("id") UUID id) {
        String url = downloadUseCase.issue(id);
        return Map.of("url", url);
    }

    /**
     * Backend-proxied export download (D2). Streams the stored bytes for a
     * GENERATED export the caller owns (tenant-scoped via {@code
     * findByIdAndTenantId} — 404 for cross-tenant), re-checking the export-type
     * permission (salary-bearing ⇒ {@code SALARY_EXPORT}) inside the use case.
     * Emits {@code EXPORT_DOWNLOADED}/{@code FILE_DOWNLOADED} audit once.
     *
     * <p>Response: {@code 200} with {@code Content-Type} matching the artifact
     * and {@code Content-Disposition: attachment; filename="…"}; {@code 404} for
     * an unknown/cross-tenant id; {@code 403} when the salary gate is missing;
     * {@code 400} for a non-GENERATED/expired job.
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('EXPORT_READ')")
    public ResponseEntity<Resource> download(@PathVariable("id") UUID id) {
        DownloadExportFileUseCase.DownloadPayload payload = downloadFileUseCase.download(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(payload.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.bytes().length)
                .body(new ByteArrayResource(payload.bytes()));
    }

    // Intentionally isAuthenticated() (F2): CancelExportJobUseCase authorizes by
    // tenant ownership only (findByIdAndTenantId → 404 cross-tenant) and checks
    // NO permission code, so there is no code to mirror here — inventing one would
    // diverge from the enforced behavior.
    @PostMapping("/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ExportJobResponse cancel(@PathVariable("id") UUID id) {
        return cancelUseCase.cancel(id);
    }
}
