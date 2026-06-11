package uz.hrlab.grading.gradestructure.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.gradestructure.application.ArchiveGradeTemplateUseCase;
import uz.hrlab.grading.gradestructure.application.GradeTemplateCatalog;
import uz.hrlab.grading.gradestructure.application.UpdateGradeTemplateUseCase;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Create-from-template picker catalog + tenant CUSTOM grade-template management
 * (BE-9). EXACT mirror of {@code MethodologyTemplateController}.
 *
 * <p>{@code GET /api/v1/grade-structure-templates} returns the UNION of global,
 * read-only built-ins ({@code GradeStructureTemplateRegistry}) and the tenant's
 * ACTIVE DB-backed custom templates ({@code grade_templates}), each tagged with
 * {@code source} / {@code is_builtin} and (for custom) the row {@code id}.
 *
 * <p>{@code PUT} (rename) + {@code DELETE} (archive) are custom-only and gated by
 * {@code GRADE_EDIT}. The save-as endpoint lives on the structure
 * ({@code POST /api/v1/grade-structures/{id}/save-as-template}). The tenant
 * always comes from {@code TenantContext}; built-ins can never be edited/archived
 * (no DB row → 404).
 */
@RestController
@RequestMapping("/api/v1/grade-structure-templates")
public class GradeStructureTemplateController {

    private final GradeTemplateCatalog templateCatalog;
    private final UpdateGradeTemplateUseCase updateTemplate;
    private final ArchiveGradeTemplateUseCase archiveTemplate;

    public GradeStructureTemplateController(GradeTemplateCatalog templateCatalog,
                                            UpdateGradeTemplateUseCase updateTemplate,
                                            ArchiveGradeTemplateUseCase archiveTemplate) {
        this.templateCatalog = templateCatalog;
        this.updateTemplate = updateTemplate;
        this.archiveTemplate = archiveTemplate;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('GRADE_READ')")
    public Map<String, List<GradeStructureTemplateResponse>> list() {
        TenantContext ctx = TenantContextHolder.requireActive();
        List<GradeStructureTemplateResponse> items = templateCatalog.list(ctx.tenantId()).stream()
                .map(GradeStructureTemplateResponse::from)
                .toList();
        return Map.of("items", items);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public GradeStructureTemplateCreatedResponse rename(
            @PathVariable UUID id, @Valid @RequestBody UpdateGradeTemplateRequest req) {
        UUID templateId = updateTemplate.rename(id, req.nameI18n(), req.descriptionI18n());
        return new GradeStructureTemplateCreatedResponse(templateId);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('GRADE_EDIT')")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        archiveTemplate.archive(id);
        return ResponseEntity.noContent().build();
    }
}
