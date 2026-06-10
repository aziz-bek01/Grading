package uz.hrlab.grading.methodology.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.methodology.application.MethodologyTemplateRegistry;

import java.util.List;
import java.util.Map;

/**
 * Read-only catalog of built-in methodology templates (slice B1).
 *
 * <p>Backs the frontend create-from-template picker
 * ({@code methodologyApi.fetchMethodologyTemplates}); before this controller the
 * path {@code GET /api/v1/methodology-templates} had no handler and 404'd (see
 * {@code GlobalExceptionHandler#handleNoResource} note). Templates are the
 * in-memory {@link MethodologyTemplateRegistry} skeletons — no persistence, no
 * tenant scoping (the catalog is global), no new table (DB-backed custom
 * templates are a later slice E). Tenant-scoped methodology instances are created
 * via {@code POST /api/v1/methodologies/from-template}.
 */
@RestController
@RequestMapping("/api/v1/methodology-templates")
public class MethodologyTemplateController {

    private final MethodologyTemplateRegistry registry;

    public MethodologyTemplateController(MethodologyTemplateRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('METHODOLOGY_READ')")
    public Map<String, List<MethodologyTemplateResponse>> list() {
        List<MethodologyTemplateResponse> items = registry.list().stream()
                .map(MethodologyTemplateResponse::from)
                .toList();
        return Map.of("items", items);
    }
}
