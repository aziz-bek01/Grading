package uz.hrlab.grading.access.domain;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.application.AbacRequest;
import uz.hrlab.grading.access.application.PolicyDecision;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("abac")
class ProjectMembershipPolicyTest {

    private final ProjectMembershipPolicy policy = new ProjectMembershipPolicy();

    @Test
    void notApplicableWithoutProjectId() {
        AbacRequest req = new AbacRequest(ctxRoles(Set.of(RoleCodes.VIEWER), Set.of()),
                "X", null, null, null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.NOT_APPLICABLE);
    }

    @Test
    void superAdminBypasses() {
        UUID projectId = UUID.randomUUID();
        AbacRequest req = new AbacRequest(
                ctxRoles(Set.of(RoleCodes.HRLAB_SUPER_ADMIN), Set.of()),
                "Project", projectId, projectId, null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void deniesWhenProjectNotInActiveSet() {
        UUID requested = UUID.randomUUID();
        UUID assigned = UUID.randomUUID();
        // EVALUATION_COMMITTEE_MEMBER is a project-scoped role — must be denied
        // when requested project is not in their membership set.
        AbacRequest req = new AbacRequest(
                ctxRoles(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of(assigned)),
                "Project", requested, requested, null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void permitsWhenProjectMatches() {
        UUID projectId = UUID.randomUUID();
        AbacRequest req = new AbacRequest(
                ctxRoles(Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), Set.of(projectId)),
                "Project", projectId, projectId, null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void deniesWhenActiveProjectsEmptyForTenantScopedRole() {
        UUID projectId = UUID.randomUUID();
        AbacRequest req = new AbacRequest(
                ctxRoles(Set.of(RoleCodes.VIEWER), Set.of()),
                "Project", projectId, projectId, null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.DENY);
    }

    // ---------- F-201 — tenant-wide bypass roles ----------

    @Test
    void clientCompanyAdminBypassesEvenWithEmptyProjectSet() {
        UUID projectId = UUID.randomUUID();
        AbacRequest req = new AbacRequest(
                ctxRoles(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of()),
                "Project", projectId, projectId, null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void clientHrDirectorBypassesEvenWithEmptyProjectSet() {
        UUID projectId = UUID.randomUUID();
        AbacRequest req = new AbacRequest(
                ctxRoles(Set.of(RoleCodes.CLIENT_HR_DIRECTOR), Set.of()),
                "Project", projectId, projectId, null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void clientCompanyAdminBypassesEvenForUnlistedProject() {
        UUID requested = UUID.randomUUID();
        UUID someOther = UUID.randomUUID();
        AbacRequest req = new AbacRequest(
                ctxRoles(Set.of(RoleCodes.CLIENT_COMPANY_ADMIN), Set.of(someOther)),
                "Project", requested, requested, null, null);
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    private TenantContext ctxRoles(Set<String> roles, Set<UUID> projects) {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(),
                projects, roles, Set.of(), Set.of(), false, "ru-RU");
    }
}
