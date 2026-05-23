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
class ApprovedEntityFilterPolicyTest {

    private final ApprovedEntityFilterPolicy policy = new ApprovedEntityFilterPolicy();

    @Test
    void notApplicableForNonViewerRoles() {
        AbacRequest req = new AbacRequest(ctx(Set.of(RoleCodes.HRLAB_CONSULTANT)),
                "Position", UUID.randomUUID(), null, null, "DRAFT");
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.NOT_APPLICABLE);
    }

    @Test
    void deniesViewerOnDraft() {
        AbacRequest req = new AbacRequest(ctx(Set.of(RoleCodes.VIEWER)),
                "Position", UUID.randomUUID(), null, null, "DRAFT");
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void permitsViewerOnActive() {
        AbacRequest req = new AbacRequest(ctx(Set.of(RoleCodes.VIEWER)),
                "Position", UUID.randomUUID(), null, null, "ACTIVE");
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void deniesExternalAuditorOnDraft() {
        AbacRequest req = new AbacRequest(ctx(Set.of(RoleCodes.EXTERNAL_AUDITOR)),
                "Position", UUID.randomUUID(), null, null, "DRAFT");
        assertThat(policy.evaluate(req)).isEqualTo(PolicyDecision.DENY);
    }

    private TenantContext ctx(Set<String> roles) {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(),
                Set.of(), roles, Set.of(), Set.of(), false, "ru-RU");
    }
}
