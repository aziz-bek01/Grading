package uz.hrlab.grading.methodology.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.methodology.application.ArchiveCustomTemplateUseCase;
import uz.hrlab.grading.methodology.application.SaveAsTemplateCommand;
import uz.hrlab.grading.methodology.application.SaveAsTemplateUseCase;
import uz.hrlab.grading.methodology.application.TemplateCatalog;
import uz.hrlab.grading.methodology.application.UpdateCustomTemplateUseCase;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Create-from-template picker catalog + tenant CUSTOM template management
 * (Epic E, extends slice B1).
 *
 * <p>{@code GET /api/v1/methodology-templates} returns the UNION of global,
 * read-only built-ins ({@code MethodologyTemplateRegistry}) and the tenant's
 * ACTIVE DB-backed custom templates ({@code methodology_templates}), each tagged
 * with {@code source} / {@code is_builtin} and (for custom) the row {@code id}.
 *
 * <p>{@code POST} (save-as-template) is gated by {@code METHODOLOGY_CREATE};
 * {@code PUT} (rename) + {@code DELETE} (archive) are custom-only and gated by
 * {@code METHODOLOGY_EDIT}. The tenant always comes from {@code TenantContext};
 * built-ins can never be edited/archived (no DB row → 404).
 */
@RestController
@RequestMapping("/api/v1/methodology-templates")
public class MethodologyTemplateController {

    private final TemplateCatalog templateCatalog;
    private final SaveAsTemplateUseCase saveAsTemplate;
    private final UpdateCustomTemplateUseCase updateCustomTemplate;
    private final ArchiveCustomTemplateUseCase archiveCustomTemplate;

    public MethodologyTemplateController(TemplateCatalog templateCatalog,
                                         SaveAsTemplateUseCase saveAsTemplate,
                                         UpdateCustomTemplateUseCase updateCustomTemplate,
                                         ArchiveCustomTemplateUseCase archiveCustomTemplate) {
        this.templateCatalog = templateCatalog;
        this.saveAsTemplate = saveAsTemplate;
        this.updateCustomTemplate = updateCustomTemplate;
        this.archiveCustomTemplate = archiveCustomTemplate;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('METHODOLOGY_READ')")
    public Map<String, List<MethodologyTemplateResponse>> list() {
        TenantContext ctx = TenantContextHolder.requireActive();
        List<MethodologyTemplateResponse> items = templateCatalog.list(ctx.tenantId()).stream()
                .map(MethodologyTemplateResponse::from)
                .toList();
        return Map.of("items", items);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('METHODOLOGY_CREATE')")
    public ResponseEntity<MethodologyTemplateCreatedResponse> saveAsTemplate(
            @Valid @RequestBody SaveAsTemplateRequest req) {
        UUID templateId = saveAsTemplate.saveAsTemplate(new SaveAsTemplateCommand(
                req.sourceVersionId(), req.code(), req.nameI18n(), req.descriptionI18n()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new MethodologyTemplateCreatedResponse(templateId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT')")
    public MethodologyTemplateCreatedResponse rename(
            @PathVariable UUID id, @Valid @RequestBody UpdateCustomTemplateRequest req) {
        UUID templateId = updateCustomTemplate.rename(id, req.nameI18n(), req.descriptionI18n());
        return new MethodologyTemplateCreatedResponse(templateId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('METHODOLOGY_EDIT')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        archiveCustomTemplate.archive(id);
        return ResponseEntity.noContent().build();
    }
}
