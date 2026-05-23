package uz.hrlab.grading.tenancy.application;

/**
 * Per-thread holder of the current {@link TenantContext}.
 *
 * <p>Populated by {@code TenantContextFilter} on request entry and cleared in
 * a {@code finally} block. Service code calls {@link #requireActive()} on
 * entry — missing context is a programming/security error
 * (security-blueprint §5.1).
 */
public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() { }

    public static void set(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext get() {
        return CONTEXT.get();
    }

    public static TenantContext requireActive() {
        TenantContext ctx = CONTEXT.get();
        if (ctx == null || ctx.tenantId() == null) {
            // Sanitized — never expose details to the client.
            throw new IllegalStateException("No active tenant context");
        }
        return ctx;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
