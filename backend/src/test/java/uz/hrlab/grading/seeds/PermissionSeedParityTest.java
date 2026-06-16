package uz.hrlab.grading.seeds;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;
import uz.hrlab.grading.access.application.PermissionCodes;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard against "phantom" permission codes — a permission that is
 * DEFINED in {@link PermissionCodes} (and typically enforced by a
 * {@code @PreAuthorize} / {@code hasPermission} check) but never made reachable
 * because no seed grants it. That is exactly the class of bug behind the
 * CLIENT_* defect (codes gated on {@code AdminClientCompanyController} but never
 * inserted into {@code public.permissions} nor granted to any role, so a
 * fail-closed authority union locked the feature out).
 *
 * <p>This is an OFFLINE unit test (no Spring context, no DB): it reflects the
 * constants from {@link PermissionCodes} and scans the seed YAML resources on
 * the classpath. It runs in the fast unit pack so a future enforced-but-ungranted
 * code fails the build before it can ship.
 *
 * <h3>What "granted in at least one seed" means here</h3>
 * Grants are assembled by Liquibase in two ways:
 * <ol>
 *   <li><b>Explicit</b> — a {@code role_perm_map} VALUES tuple
 *       {@code ('ROLE','CODE')} or a {@code JOIN public.permissions p ON p.code
 *       IN ('CODE', ...)} block (seeds 004 / 007 / the per-module tenant-schema
 *       seeds).</li>
 *   <li><b>Wildcard</b> — {@code seeds/005-superadmin-permission-backfill.yaml}
 *       grants HRLAB_SUPER_ADMIN EVERY catalogue code <em>except</em> a small,
 *       deliberately-documented carve-out set (salary is per-user only; AI /
 *       payroll are unshipped). So a code is granted-by-a-seed iff it has a
 *       catalogue row AND it is not a carve-out — UNLESS it is explicitly granted
 *       elsewhere despite being a carve-out.</li>
 * </ol>
 *
 * <p>The carve-out set is not hard-coded blind: it is PARSED from seed 005's
 * {@code NOT IN (...)} list, so if the seed's exclusions ever change, this
 * test's expectation changes with it (keeping the guard honest).
 */
class PermissionSeedParityTest {

    /** Matches a catalogue insert literal: {@code gen_random_uuid(), 'CODE'}. */
    private static final Pattern CATALOG_INSERT =
            Pattern.compile("gen_random_uuid\\(\\),\\s*'([A-Z_]+)'");

    /** Matches the seed-005 carve-out exclusion list block. */
    private static final Pattern CARVE_OUT_BLOCK =
            Pattern.compile("p\\.code\\s+NOT\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    @Test
    void everyPermissionCodeIsInsertedIntoTheCatalogBySomeSeed() throws Exception {
        Set<String> defined = definedPermissionCodes();
        Set<String> cataloged = catalogInsertedCodes();

        Set<String> missing = new TreeSet<>(defined);
        missing.removeAll(cataloged);

        assertThat(missing)
                .as("PermissionCodes constants with NO `INSERT INTO public.permissions` "
                        + "row in any seed — seed 005's super-admin wildcard JOINs "
                        + "public.permissions, so a missing catalogue row means the code "
                        + "is granted to NOBODY (the CLIENT_* phantom-permission bug class). "
                        + "Add a catalogue insert (and any explicit role grants) in a seed.")
                .isEmpty();
    }

    @Test
    void everyPermissionCodeIsGrantedToAtLeastOneRole() throws Exception {
        Set<String> defined = definedPermissionCodes();
        Set<String> cataloged = catalogInsertedCodes();
        Set<String> carveOuts = superAdminCarveOuts();
        Set<String> explicitGrants = explicitRoleGrantCodes();

        // A code is granted to >=1 role iff:
        //   - it has a catalogue row AND is not a super-admin carve-out
        //     (seed 005 wildcard grants it to HRLAB_SUPER_ADMIN), OR
        //   - it is explicitly granted to some role by a tuple/JOIN seed.
        Set<String> ungranted = new TreeSet<>();
        for (String code : defined) {
            boolean grantedByWildcard = cataloged.contains(code) && !carveOuts.contains(code);
            boolean grantedExplicitly = explicitGrants.contains(code);
            if (!grantedByWildcard && !grantedExplicitly) {
                ungranted.add(code);
            }
        }

        // The ONLY codes allowed to be ungranted-by-role are the documented
        // carve-outs that are also not explicitly granted: salary (per-user,
        // never a role grant), AI assist + payroll import (unshipped). This set is
        // pinned so that if a FUTURE enforced code lands in the carve-out list by
        // mistake (making a feature unreachable), the test fails until it is
        // either granted or consciously added here with justification.
        Set<String> allowedUngranted = new TreeSet<>(Set.of(
                PermissionCodes.SALARY_VIEW,
                PermissionCodes.SALARY_EDIT,
                PermissionCodes.SALARY_EXPORT,
                PermissionCodes.SALARY_SCENARIO_RUN,
                PermissionCodes.AI_ASSIST_USE,
                PermissionCodes.PAYROLL_IMPORT));

        assertThat(ungranted)
                .as("permission codes granted to NO role by any seed. Salary is "
                        + "per-user (never role-granted); AI assist + payroll import are "
                        + "unshipped. ANY OTHER code here is an enforced-but-ungranted "
                        + "phantom — grant it to the appropriate role(s) in the right seed, "
                        + "the same fix as the CLIENT_* defect.")
                .isEqualTo(allowedUngranted);
    }

    // --------------------------------------------------------------- helpers

    private static Set<String> definedPermissionCodes() throws IllegalAccessException {
        Set<String> codes = new HashSet<>();
        for (Field f : PermissionCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    && Modifier.isFinal(f.getModifiers())
                    && f.getType() == String.class) {
                codes.add((String) f.get(null));
            }
        }
        return codes;
    }

    private static Set<String> catalogInsertedCodes() throws IOException {
        Set<String> codes = new HashSet<>();
        for (String yaml : allSeedYaml()) {
            Matcher m = CATALOG_INSERT.matcher(yaml);
            while (m.find()) {
                codes.add(m.group(1));
            }
        }
        return codes;
    }

    /**
     * Codes carved OUT of seed 005's super-admin wildcard backfill — parsed from
     * the seed's own {@code NOT IN (...)} list so the expectation tracks reality.
     */
    private static Set<String> superAdminCarveOuts() throws IOException {
        String yaml = seedYaml("seeds/005-superadmin-permission-backfill.yaml");
        Matcher m = CARVE_OUT_BLOCK.matcher(yaml);
        assertThat(m.find())
                .as("seed 005 must contain the super-admin NOT IN carve-out block")
                .isTrue();
        Set<String> carved = new TreeSet<>();
        Matcher codes = Pattern.compile("'([A-Z_]+)'").matcher(m.group(1));
        while (codes.find()) {
            carved.add(codes.group(1));
        }
        return carved;
    }

    /**
     * Codes that appear in an EXPLICIT role grant. A grant is the specific
     * INSERT-SELECT idiom used by every seed:
     * <pre>INSERT INTO public.role_permissions (role_id, permission_id) SELECT r.id, p.id ...</pre>
     * Two flavours of code source feed that SELECT:
     * <ol>
     *   <li>a {@code role_perm_map} VALUES block of {@code ('ROLE','CODE')} tuples, or</li>
     *   <li>a {@code JOIN public.permissions p ON p.code IN ('CODE', ...)} clause.</li>
     * </ol>
     *
     * <p>Anchoring on {@code SELECT r.id, p.id} is what makes this precise: the
     * seed invariants and rollbacks also reference {@code public.role_permissions}
     * with a {@code p.code IN (...)} list, but they use {@code SELECT COUNT(*)} /
     * {@code DELETE} — counting those would wrongly classify the SALARY_* /
     * PAYROLL_IMPORT carve-outs (named only in invariant/rollback lists) as
     * granted. We slice each YAML into INSERT-grant fragments first, then scan.
     */
    private static Set<String> explicitRoleGrantCodes() throws IOException {
        Set<String> granted = new HashSet<>();
        Pattern tuple = Pattern.compile("\\(\\s*'[A-Z_]+'\\s*,\\s*'([A-Z_]+)'\\s*\\)");
        Pattern codeLit = Pattern.compile("'([A-Z_]+)'");
        // p.code IN (...) ONLY inside an INSERT-grant fragment (see below).
        Pattern joinIn = Pattern.compile("p\\.code\\s+IN\\s*\\(([^)]*)\\)",
                Pattern.CASE_INSENSITIVE);
        // A grant fragment starts at the INSERT-SELECT into role_permissions and
        // runs until the next statement boundary (';' or 'rollback'/'DO $$').
        Pattern grantFragment = Pattern.compile(
                "INSERT\\s+INTO\\s+public\\.role_permissions\\b[\\s\\S]*?SELECT\\s+r\\.id,\\s*p\\.id\\b[\\s\\S]*?;",
                Pattern.CASE_INSENSITIVE);

        for (String yaml : allSeedYaml()) {
            // (1) role_perm_map VALUES tuples — the WITH block that feeds the SELECT.
            for (String block : yaml.split("(?i)role_perm_map")) {
                if (!block.contains("role_permissions")) continue;
                // Only the VALUES portion before the INSERT/SELECT keyword.
                String valuesPart = block.split("(?i)INSERT\\s+INTO")[0];
                Matcher tm = tuple.matcher(valuesPart);
                while (tm.find()) {
                    granted.add(tm.group(1));
                }
            }
            // (2) JOIN ... ON p.code IN (...) — but ONLY within an INSERT-grant fragment,
            // never an invariant SELECT COUNT(*) or a rollback DELETE.
            Matcher fm = grantFragment.matcher(yaml);
            while (fm.find()) {
                Matcher jm = joinIn.matcher(fm.group());
                while (jm.find()) {
                    Matcher cm = codeLit.matcher(jm.group(1));
                    while (cm.find()) {
                        granted.add(cm.group(1));
                    }
                }
            }
        }
        return granted;
    }

    private static String[] allSeedYamlPaths() {
        return new String[]{
                "classpath:db/changelog/seeds/*.yaml",
                "classpath:db/changelog/tenant-schema/*permission*.yaml"
        };
    }

    private static java.util.List<String> allSeedYaml() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        java.util.List<String> contents = new java.util.ArrayList<>();
        for (String pattern : allSeedYamlPaths()) {
            for (Resource r : resolver.getResources(pattern)) {
                contents.add(readResource(r));
            }
        }
        assertThat(contents).as("seed YAML resources must be on the classpath").isNotEmpty();
        return contents;
    }

    private static String seedYaml(String relative) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource r = resolver.getResource("classpath:db/changelog/" + relative);
        assertThat(r.exists()).as(relative + " must exist on the classpath").isTrue();
        return readResource(r);
    }

    private static String readResource(Resource r) throws IOException {
        try (var is = r.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
