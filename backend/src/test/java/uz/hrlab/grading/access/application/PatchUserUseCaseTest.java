package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.domain.UserStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningException;
import uz.hrlab.grading.integration.idp.application.IdentityProvisioningPort;
import uz.hrlab.grading.integration.idp.infrastructure.ZitadelIdpProperties;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Offline unit tests for the credential-edit extension of {@link PatchUserUseCase}
 * (email + password change pushed to the IdP). Pure Mockito — no Spring, no DB,
 * no network. Asserts:
 * <ul>
 *   <li>password reset requires an IdP account ({@code USER_NO_IDP_ACCOUNT}) and
 *       a complexity-valid value ({@code USER_PASSWORD_WEAK});</li>
 *   <li>email change is all-or-nothing — a failing IdP push PROPAGATES so the tx
 *       rolls back (the grading email is left flipped in-memory because rollback
 *       is the container's job; the test asserts the exception escapes);</li>
 *   <li>email uniqueness ({@code USER_EMAIL_TAKEN});</li>
 *   <li>the password value is NEVER placed in an audit reason.</li>
 * </ul>
 */
@Tag("unit")
class PatchUserUseCaseTest {

    private UserManagementPolicy policy;
    private UserRepository userRepo;
    private UserTenantMembershipRepository membershipRepo;
    private GetUserDetailsQuery detailsQuery;
    private AuditService audit;
    private IdentityProvisioningPort idp;
    private ZitadelIdpProperties idpProps;

    private PatchUserUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        policy = mock(UserManagementPolicy.class);
        userRepo = mock(UserRepository.class);
        membershipRepo = mock(UserTenantMembershipRepository.class);
        detailsQuery = mock(GetUserDetailsQuery.class);
        audit = mock(AuditService.class);
        idp = mock(IdentityProvisioningPort.class);
        idpProps = new ZitadelIdpProperties();

        useCase = new PatchUserUseCase(policy, userRepo, membershipRepo,
                detailsQuery, audit, idp, idpProps);

        when(membershipRepo.findAllByUserId(userId)).thenReturn(List.of());
        TenantContextHolder.set(new TenantContext(actorId, tenantId,
                Set.of(), Set.of(), Set.of(), Set.of(), false, "en-US"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private UserJpaEntity user(String email, String subject) {
        return new UserJpaEntity(userId, email, subject, "Jane Doe",
                UserStatus.ACTIVE, "en-US");
    }

    // --- PASSWORD ---

    @Test
    void passwordResetPushesToIdpAndAuditsWithoutTheValue() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        useCase.patch(userId, null, null, null, null, "N3wP@ssword");

        verify(idp).setPassword("zid-1", "N3wP@ssword");
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        AuditEvent e = ev.getValue();
        assertThat(e.action()).isEqualTo(AuditAction.USER_PASSWORD_RESET);
        // the secret must never leak into the audit reason
        assertThat(e.reason()).doesNotContain("N3wP@ssword");
        assertThat(e.reason()).contains("zid-1");
    }

    @Test
    void passwordResetRejectedWhenUserHasNoIdpAccount() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", null); // never provisioned
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                useCase.patch(userId, null, null, null, null, "N3wP@ssword"))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getCode())
                .isEqualTo("USER_NO_IDP_ACCOUNT");
        verify(idp, never()).setPassword(any(), any());
    }

    @Test
    void passwordResetRejectedWhenIdpDisabled() {
        idpProps.setEnabled(false);
        UserJpaEntity user = user("jane@client.uz", "zid-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                useCase.patch(userId, null, null, null, null, "N3wP@ssword"))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getCode())
                .isEqualTo("USER_NO_IDP_ACCOUNT");
        verifyNoInteractions(idp);
    }

    @Test
    void passwordResetRejectedWhenTooWeak() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() ->
                useCase.patch(userId, null, null, null, null, "weak"))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getCode())
                .isEqualTo("USER_PASSWORD_WEAK");
        verify(idp, never()).setPassword(any(), any());
    }

    @Test
    void blankPasswordIsIgnored() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        useCase.patch(userId, null, null, null, null, "   ");

        verify(idp, never()).setPassword(any(), any());
    }

    @Test
    void notInitializedReasonFromIdpPropagatesAsActionable400() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-legacy");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new IdentityProvisioningException(
                IdentityProvisioningException.Reason.USER_NOT_INITIALIZED,
                "not initialised", null))
                .when(idp).setPassword(eq("zid-legacy"), eq("N3wP@ssword"));

        // The specific, actionable reason propagates so the API returns a clear
        // 400 USER_IDP_NOT_INITIALIZED rather than a confusing 502.
        assertThatThrownBy(() ->
                useCase.patch(userId, null, null, null, null, "N3wP@ssword"))
                .isInstanceOf(IdentityProvisioningException.class)
                .satisfies(ex -> {
                    IdentityProvisioningException ipe = (IdentityProvisioningException) ex;
                    assertThat(ipe.isActionable()).isTrue();
                    assertThat(ipe.getCode()).isEqualTo("USER_IDP_NOT_INITIALIZED");
                });
    }

    @Test
    void passwordFailureLeavesProfileUnsaved_failFast() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-legacy");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        doThrow(new IdentityProvisioningException(
                IdentityProvisioningException.Reason.USER_NOT_INITIALIZED,
                "not initialised", null))
                .when(idp).setPassword(eq("zid-legacy"), eq("N3wP@ssword"));

        // A profile field is changed alongside the password. Because the IdP push
        // runs BEFORE any DB write, the failure means NOTHING is half-saved.
        assertThatThrownBy(() ->
                useCase.patch(userId, "New Name", null, null, null, "N3wP@ssword"))
                .isInstanceOf(IdentityProvisioningException.class);

        verify(userRepo, never()).save(any());
        // no USER_PASSWORD_RESET / USER_UPDATED audit was written either
        verify(audit, never()).record(any());
    }

    // --- EMAIL ---

    @Test
    void emailChangeUpdatesGradingThenIdpAndAudits() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("old@client.uz", "zid-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.findByEmailIgnoreCase("new@client.uz")).thenReturn(Optional.empty());

        useCase.patch(userId, null, null, null, "new@client.uz", null);

        // grading email lower-cased + saved
        assertThat(user.getEmail()).isEqualTo("new@client.uz");
        verify(userRepo).save(user);
        verify(idp).changeEmail("zid-1", "new@client.uz");

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(2)).record(ev.capture()); // USER_UPDATED + USER_EMAIL_CHANGED
        AuditEvent emailEv = ev.getAllValues().stream()
                .filter(e -> AuditAction.USER_EMAIL_CHANGED.equals(e.action()))
                .findFirst().orElseThrow();
        assertThat(emailEv.reason()).contains("from=old@client.uz").contains("to=new@client.uz");
    }

    @Test
    void emailChangeRollsBackWhenIdpRejects() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("old@client.uz", "zid-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.findByEmailIgnoreCase("new@client.uz")).thenReturn(Optional.empty());
        doThrow(new IdentityProvisioningException("rejected"))
                .when(idp).changeEmail(eq("zid-1"), eq("new@client.uz"));

        // The exception PROPAGATES → @Transactional rolls back the grading email.
        assertThatThrownBy(() ->
                useCase.patch(userId, null, null, null, "new@client.uz", null))
                .isInstanceOf(IdentityProvisioningException.class);
    }

    @Test
    void emailChangeRejectedWhenTakenByAnotherUser() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("old@client.uz", "zid-1");
        UserJpaEntity other = new UserJpaEntity(UUID.randomUUID(), "new@client.uz",
                "zid-2", "Other", UserStatus.ACTIVE, "en-US");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.findByEmailIgnoreCase("new@client.uz")).thenReturn(Optional.of(other));

        assertThatThrownBy(() ->
                useCase.patch(userId, null, null, null, "new@client.uz", null))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getCode())
                .isEqualTo("USER_EMAIL_TAKEN");
        verify(idp, never()).changeEmail(any(), any());
    }

    @Test
    void sameEmailDifferentCaseIsNoChange() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        useCase.patch(userId, null, null, null, "JANE@CLIENT.UZ", null);

        verify(idp, never()).changeEmail(any(), any());
        verify(userRepo, never()).save(any());
    }

    @Test
    void emailChangeWithIdpDisabledUpdatesGradingOnly() {
        idpProps.setEnabled(false);
        UserJpaEntity user = user("old@client.uz", "zid-1");
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.findByEmailIgnoreCase("new@client.uz")).thenReturn(Optional.empty());

        useCase.patch(userId, null, null, null, "new@client.uz", null);

        assertThat(user.getEmail()).isEqualTo("new@client.uz");
        verify(idp, never()).changeEmail(any(), any());
    }
}
