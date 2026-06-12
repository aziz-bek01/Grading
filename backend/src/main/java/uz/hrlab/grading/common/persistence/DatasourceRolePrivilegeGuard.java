package uz.hrlab.grading.common.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Fail-closed boot-time guard (BE-1, RLS gap closure — 2026-06-12).
 *
 * <p>PostgreSQL exempts roles with {@code rolsuper} or {@code rolbypassrls}
 * from Row-Level Security — even when a table has {@code FORCE ROW LEVEL
 * SECURITY}. If the application runtime ever connects as such a role in a
 * <em>deployed</em> environment, the entire RLS defense-in-depth layer
 * (changelogs 030/031, the {@code app.tenant_id} GUC, the NULLIF policies)
 * is silently disabled and a single forgotten tenant predicate or an unbound
 * GUC leaks cross-tenant rows. That is exactly the failure mode behind the
 * approvals cross-tenant inbox leak (the dev/demo runtime ran as the
 * bootstrap superuser {@code grading_app}).
 *
 * <p>This guard runs once on {@link ApplicationReadyEvent} and queries
 * {@code pg_roles} for the EFFECTIVE datasource role ({@code current_user}).
 * In any profile OTHER than {@code local}/{@code test} it FAILS STARTUP
 * (throws) if that role is {@code rolsuper} or {@code rolbypassrls}. Local
 * developer convenience (a single superuser that also runs Liquibase) and the
 * Testcontainers superuser are the only permitted exceptions — and they are
 * never deployed.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Runs on {@code ApplicationReadyEvent} (after the context is fully
 *       refreshed and the datasource is live) so the JDBC query is safe.</li>
 *   <li>If the privilege columns cannot be read (e.g. an unexpected driver or
 *       a non-Postgres datasource in some future profile) the guard fails
 *       CLOSED in deployed profiles — an unverifiable role is treated as
 *       unsafe — and merely logs in local/test.</li>
 * </ul>
 */
@Component
public class DatasourceRolePrivilegeGuard
        implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log =
            LoggerFactory.getLogger(DatasourceRolePrivilegeGuard.class);

    /**
     * Profiles where a superuser/bypassrls runtime role is tolerated.
     * {@code local} is the developer single-superuser convenience (see
     * application-local.yml) and {@code test} is the Testcontainers superuser
     * — never deployed. {@code migrate} is exempt BY DESIGN: the one-shot
     * Liquibase migrator runs as {@code grading_migrator} which carries
     * {@code rolbypassrls=true} (it must touch every tenant's rows for
     * cross-tenant backfills), serves no requests, and exits right after the
     * changelog applies. Every REQUEST-SERVING profile (dev, demo, staging,
     * prod, CI smoke) MUST run as a NON-superuser, NON-bypassrls role so
     * FORCE RLS actually applies.
     */
    private static final Set<String> RLS_EXEMPT_PROFILES = Set.of("local", "test", "migrate");

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public DatasourceRolePrivilegeGuard(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        boolean rlsExemptProfile = isRlsExemptProfile();

        RolePrivilege privilege = readEffectiveRolePrivilege(rlsExemptProfile);
        if (privilege == null) {
            // Could not determine privileges. Local/test: log and continue.
            // Deployed: readEffectiveRolePrivilege already threw (fail-closed).
            return;
        }

        boolean bypassesRls = privilege.superuser() || privilege.bypassRls();
        if (bypassesRls && !rlsExemptProfile) {
            throw new IllegalStateException(
                    "FATAL: runtime datasource role '" + privilege.roleName()
                            + "' has rolsuper=" + privilege.superuser()
                            + " / rolbypassrls=" + privilege.bypassRls()
                            + " in a non-local profile "
                            + String.join(",", environment.getActiveProfiles())
                            + ". Such a role BYPASSES FORCE ROW LEVEL SECURITY and "
                            + "silently disables tenant isolation. Deploy the app as "
                            + "grading_runtime (NON-superuser, NON-bypassrls) — see "
                            + "db/role-grants-matrix.md. Refusing to start.");
        }

        if (bypassesRls) {
            log.warn("Datasource role '{}' bypasses RLS (rolsuper={}, rolbypassrls={}) — "
                            + "tolerated ONLY because the active profile is local/test. "
                            + "NEVER deploy with this role.",
                    privilege.roleName(), privilege.superuser(), privilege.bypassRls());
        } else {
            log.info("Datasource role '{}' is RLS-subject (rolsuper=false, rolbypassrls=false) — "
                    + "FORCE ROW LEVEL SECURITY tenant isolation is active.",
                    privilege.roleName());
        }
    }

    private boolean isRlsExemptProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            // No explicit profile -> Spring default is 'local' (application.yml
            // spring.profiles.default: local) — exempt.
            return true;
        }
        for (String p : active) {
            if (RLS_EXEMPT_PROFILES.contains(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reads {@code rolsuper}/{@code rolbypassrls} for the effective datasource
     * role ({@code current_user}). Returns {@code null} only in RLS-exempt
     * profiles when the query fails; in deployed profiles an unreadable
     * privilege is fail-closed (throws).
     */
    private RolePrivilege readEffectiveRolePrivilege(boolean rlsExemptProfile) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT current_user AS role_name, rolsuper, rolbypassrls "
                            + "FROM pg_roles WHERE rolname = current_user",
                    (rs, rowNum) -> new RolePrivilege(
                            rs.getString("role_name"),
                            rs.getBoolean("rolsuper"),
                            rs.getBoolean("rolbypassrls")));
        } catch (RuntimeException ex) {
            if (rlsExemptProfile) {
                log.warn("Could not verify datasource role RLS privileges in an "
                        + "RLS-exempt profile — continuing.", ex);
                return null;
            }
            throw new IllegalStateException(
                    "FATAL: could not verify datasource role RLS privileges in a "
                            + "non-local profile. An unverifiable runtime role is treated "
                            + "as unsafe (fail-closed). Refusing to start.", ex);
        }
    }

    private record RolePrivilege(String roleName, boolean superuser, boolean bypassRls) {
    }
}
