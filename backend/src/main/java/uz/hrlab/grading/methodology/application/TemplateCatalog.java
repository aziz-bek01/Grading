package uz.hrlab.grading.methodology.application;

import java.util.List;
import java.util.UUID;

/**
 * Single port over the unified methodology-template catalog (Epic E): the UNION
 * of global, read-only built-ins ({@code MethodologyTemplateRegistry}) and the
 * tenant's DB-backed CUSTOM templates ({@code methodology_templates}).
 *
 * <p>This port is the seam that lets {@code CreateMethodologyFromTemplateUseCase}
 * instantiate from EITHER source without duplicating the deep-copy logic: both
 * built-in and custom resolve to the SAME {@link MethodologyTemplate}
 * instantiation shape (factors + levels), and the use case copies that one
 * shape into a new methodology v1.
 *
 * <p>Tenant scoping: the {@code tenantId} is always supplied by the caller from
 * the active {@code TenantContext} (never the request body). Built-ins are
 * tenant-agnostic; custom templates are filtered to the given tenant (RLS is
 * defense-in-depth, the predicate is explicit) so a cross-tenant code is never
 * visible in the catalog and never instantiable.
 */
public interface TemplateCatalog {

    /**
     * Catalog for the picker: built-ins (global, read-only) UNION the tenant's
     * ACTIVE custom templates. Built-ins first, in their stable registry order,
     * then custom templates.
     */
    List<TemplateCatalogEntry> list(UUID tenantId);

    /**
     * Resolve a template to its common instantiation shape by code. Tries the
     * built-in registry first, then the tenant's ACTIVE custom templates. Throws
     * {@link uz.hrlab.grading.common.exception.ResourceNotFoundException} (404)
     * when no built-in and no ACTIVE custom template of that code exists for the
     * tenant — a cross-tenant or archived custom code is therefore not
     * instantiable and not distinguishable from "does not exist".
     */
    MethodologyTemplate findInstantiable(UUID tenantId, String code);

    /**
     * Whether a code is a reserved built-in code (CLASSIC_8_FACTOR /
     * EXTENDED_11_CRITERIA / CUSTOM). Save-as-template rejects a custom code that
     * collides with a built-in so the global namespace stays unambiguous.
     */
    boolean isBuiltinCode(String code);
}
