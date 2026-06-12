package uz.hrlab.grading.methodology.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.access.application.ActorNameResolver;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.UUID;

/**
 * Resolves user display names from their UUID for audit-friendly UX
 * (defect D-407 / F-411). The methodology read paths inject this to populate
 * {@code lockedByName} / {@code approvedByName} on
 * {@link uz.hrlab.grading.methodology.api.MethodologyVersionResponse}.
 *
 * <p><b>Item 4 — NO DUPLICATION.</b> This class no longer owns any lookup logic;
 * it is a thin, tenant-scoping delegate over the shared
 * {@link ActorNameResolver} (single source of truth for user-name resolution
 * across approval, methodology, workflow, and future comments). The tenant id is
 * sourced from the active {@link TenantContext} — every caller resolves a
 * tenant-derived id ({@code approvedBy} / {@code lockedBy} read off a
 * tenant-loaded version row) inside an authenticated request, so the membership
 * check in {@link ActorNameResolver} applies and a cross-tenant name can never
 * leak.
 *
 * <p>Returns {@code null} when the user is unknown or holds no active membership
 * in the active tenant — the frontend falls back to an "Unknown actor" label.
 */
@Component
public class MethodologyActorNameResolver {

    private final ActorNameResolver delegate;

    public MethodologyActorNameResolver(ActorNameResolver delegate) {
        this.delegate = delegate;
    }

    /** @return the user's full name, or {@code null} if id is null/unknown/non-member. */
    public String resolve(UUID userId) {
        if (userId == null) {
            return null;
        }
        TenantContext ctx = TenantContextHolder.requireActive();
        return delegate.resolve(ctx.tenantId(), userId);
    }
}
