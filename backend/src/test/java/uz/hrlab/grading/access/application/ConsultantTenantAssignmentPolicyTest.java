package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("abac")
class ConsultantTenantAssignmentPolicyTest {

    @Test
    void superAdminBypassesMembershipCheck() {
        UserTenantMembershipRepository repo = mock(UserTenantMembershipRepository.class);
        ConsultantTenantAssignmentPolicy policy = new ConsultantTenantAssignmentPolicy(repo);
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_SUPER_ADMIN));
        assertThat(policy.evaluate(new AbacRequest(ctx, "X", null, null, null, null)))
                .isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void denyConsultantWithoutMembership() {
        UserTenantMembershipRepository repo = mock(UserTenantMembershipRepository.class);
        when(repo.existsByUserIdAndTenantId(any(), any())).thenReturn(false);
        ConsultantTenantAssignmentPolicy policy = new ConsultantTenantAssignmentPolicy(repo);
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_CONSULTANT));
        assertThat(policy.evaluate(new AbacRequest(ctx, "X", null, null, null, null)))
                .isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void permitConsultantWithMembership() {
        UserTenantMembershipRepository repo = mock(UserTenantMembershipRepository.class);
        TenantContext ctx = ctx(Set.of(RoleCodes.HRLAB_CONSULTANT));
        when(repo.existsByUserIdAndTenantId(eq(ctx.userId()), eq(ctx.tenantId())))
                .thenReturn(true);
        ConsultantTenantAssignmentPolicy policy = new ConsultantTenantAssignmentPolicy(repo);
        assertThat(policy.evaluate(new AbacRequest(ctx, "X", null, null, null, null)))
                .isEqualTo(PolicyDecision.PERMIT);
    }

    @Test
    void notApplicableForClientRoles() {
        UserTenantMembershipRepository repo = mock(UserTenantMembershipRepository.class);
        ConsultantTenantAssignmentPolicy policy = new ConsultantTenantAssignmentPolicy(repo);
        TenantContext ctx = ctx(Set.of(RoleCodes.CLIENT_HR_DIRECTOR));
        assertThat(policy.evaluate(new AbacRequest(ctx, "X", null, null, null, null)))
                .isEqualTo(PolicyDecision.NOT_APPLICABLE);
    }

    private TenantContext ctx(Set<String> roles) {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(),
                Set.of(), roles, Set.of(), Set.of(), false, "ru-RU");
    }
}
