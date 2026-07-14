package uz.hrlab.grading.access.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uz.hrlab.grading.access.domain.MembershipStatus;
import uz.hrlab.grading.access.domain.UserStatus;
import uz.hrlab.grading.access.infrastructure.UserJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserRepository;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipJpaEntity;
import uz.hrlab.grading.access.infrastructure.UserTenantMembershipRepository;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.ValidationException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Offline unit tests for {@link ResetLoginUseCase} — the "Reset login / Re-send
 * invitation" recovery action. Pure Mockito (no Spring, no DB, no network).
 * Asserts the four behaviours called out in the brief:
 * <ul>
 *   <li>create-fresh path: re-provision by the current email sets the subject,
 *       sets the password, flips INVITED→ACTIVE, audits USER_LOGIN_RESET without
 *       the value;</li>
 *   <li>mis-linked path: the resolved subject differs from the stored one ⇒ the
 *       grading user is RE-LINKED, and the old subject is NEVER deleted (the port
 *       has no delete method and none is invoked);</li>
 *   <li>weak password is rejected ({@code USER_INVITE_WEAK_PASSWORD}) before any
 *       IdP call;</li>
 *   <li>flag-off ⇒ {@code USER_NO_IDP_ACCOUNT}, IdP never touched;</li>
 *   <li>an actionable USER_IDP_NOT_INITIALIZED from setPassword propagates (→400,
 *       not 502).</li>
 * </ul>
 */
@Tag("unit")
class ResetLoginUseCaseTest {

    private UserManagementPolicy policy;
    private UserRepository userRepo;
    private UserTenantMembershipRepository membershipRepo;
    private GetUserDetailsQuery detailsQuery;
    private AuditService audit;
    private IdentityProvisioningPort idp;
    private ZitadelIdpProperties idpProps;

    private ResetLoginUseCase useCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    private static final String PW = "N3wP@ssword";

    @BeforeEach
    void setUp() {
        policy = mock(UserManagementPolicy.class);
        userRepo = mock(UserRepository.class);
        membershipRepo = mock(UserTenantMembershipRepository.class);
        detailsQuery = mock(GetUserDetailsQuery.class);
        audit = mock(AuditService.class);
        idp = mock(IdentityProvisioningPort.class);
        idpProps = new ZitadelIdpProperties();

        useCase = new ResetLoginUseCase(policy, userRepo, membershipRepo,
                detailsQuery, audit, idp, idpProps);

        when(membershipRepo.findAllByUserId(userId)).thenReturn(List.of());
        TenantContextHolder.set(new TenantContext(actorId, tenantId,
                Set.of(), Set.of(), Set.of(), Set.of(), false, "en-US"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private UserJpaEntity user(String email, String subject, UserStatus status) {
        return new UserJpaEntity(userId, email, subject, "Jane Doe", status, "en-US");
    }

    private UserTenantMembershipJpaEntity membership(MembershipStatus status) {
        return new UserTenantMembershipJpaEntity(UUID.randomUUID(), userId, tenantId,
                status, false);
    }

    // --- create-fresh path (no Zitadel account yet) ---

    @Test
    void createFreshPathSetsSubjectPasswordActivatesAndAuditsWithoutValue() {
        idpProps.setEnabled(true);
        // INVITED user with NO subject yet (USER_STATE_INITIAL mirror) + INVITED membership.
        UserJpaEntity user = user("jane@client.uz", null, UserStatus.INVITED);
        UserTenantMembershipJpaEntity m = membership(MembershipStatus.INVITED);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepo.findAllByUserId(userId)).thenReturn(List.of(m));
        // create-or-link by email returns a fresh account.
        when(idp.provisionUser(eq("jane@client.uz"), any(), any(), eq(PW)))
                .thenReturn(new IdentityProvisioningPort.ProvisionResult("zid-fresh", false));

        useCase.resetLogin(userId, PW);

        // subject persisted, password set on the resolved account, status flipped.
        assertThat(user.getExternalIdpSubject()).isEqualTo("zid-fresh");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(m.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(idp).provisionUser(eq("jane@client.uz"), any(), any(), eq(PW));
        verify(idp).setPassword("zid-fresh", PW);
        verify(userRepo).save(user);
        verify(membershipRepo).save(m);

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        AuditEvent e = ev.getValue();
        assertThat(e.action()).isEqualTo(AuditAction.USER_LOGIN_RESET);
        assertThat(e.reason()).contains("zid-fresh").contains("jane@client.uz");
        // the secret must never leak into the audit reason
        assertThat(e.reason()).doesNotContain(PW);
    }

    // --- mis-linked path (stored subject points to the WRONG account) ---

    @Test
    void misLinkedPathReLinksToCorrectAccountWithoutDeletingOld() {
        idpProps.setEnabled(true);
        // Stored subject is the WRONG one (legacy mis-link). User already ACTIVE.
        UserJpaEntity user = user("asr.3x10@gmail.com", "zid-wrong-azasqarov",
                UserStatus.ACTIVE);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        // Re-provision by the CURRENT grading email links the CORRECT account.
        when(idp.provisionUser(eq("asr.3x10@gmail.com"), any(), any(), eq(PW)))
                .thenReturn(new IdentityProvisioningPort.ProvisionResult("zid-correct-asr", true));

        useCase.resetLogin(userId, PW);

        // re-linked to the correct subject and password set on it
        assertThat(user.getExternalIdpSubject()).isEqualTo("zid-correct-asr");
        verify(idp).setPassword("zid-correct-asr", PW);
        verify(userRepo).save(user);

        // the old/mis-linked account is NEVER deleted (port has no delete; assert
        // none of the lifecycle calls were made against the old subject).
        verify(idp, never()).deactivateUser(any());
        verify(idp, never()).deactivateUser("zid-wrong-azasqarov");

        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().reason()).contains("reLinked=true");
    }

    @Test
    void sameSubjectIsNotReLinked() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-same", UserStatus.ACTIVE);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(idp.provisionUser(eq("jane@client.uz"), any(), any(), eq(PW)))
                .thenReturn(new IdentityProvisioningPort.ProvisionResult("zid-same", true));

        useCase.resetLogin(userId, PW);

        assertThat(user.getExternalIdpSubject()).isEqualTo("zid-same");
        verify(idp).setPassword("zid-same", PW);
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().reason()).contains("reLinked=false");
    }

    // --- validation / flag-off ---

    @Test
    void weakPasswordRejectedBeforeAnyIdpCall() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-1", UserStatus.ACTIVE);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.resetLogin(userId, "weak"))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getCode())
                .isEqualTo("USER_INVITE_WEAK_PASSWORD");
        verifyNoInteractions(idp);
        verify(userRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void flagOffRejectsWithNoIdpAccountAndNeverTouchesIdp() {
        idpProps.setEnabled(false);
        UserJpaEntity user = user("jane@client.uz", "zid-1", UserStatus.ACTIVE);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> useCase.resetLogin(userId, PW))
                .isInstanceOf(ValidationException.class)
                .extracting(ex -> ((ValidationException) ex).getCode())
                .isEqualTo("USER_NO_IDP_ACCOUNT");
        verifyNoInteractions(idp);
        verify(userRepo, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void notInitializedFromSetPasswordPropagatesAsActionable400() {
        idpProps.setEnabled(true);
        // provisionUser links to an existing account that is itself still INITIAL
        // with that exact email; setPassword on it still fails → actionable 400.
        UserJpaEntity user = user("jane@client.uz", "zid-initial", UserStatus.ACTIVE);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(idp.provisionUser(eq("jane@client.uz"), any(), any(), eq(PW)))
                .thenReturn(new IdentityProvisioningPort.ProvisionResult("zid-initial", true));
        doThrow(new IdentityProvisioningException(
                IdentityProvisioningException.Reason.USER_NOT_INITIALIZED,
                "not initialised", null))
                .when(idp).setPassword(eq("zid-initial"), eq(PW));

        assertThatThrownBy(() -> useCase.resetLogin(userId, PW))
                .isInstanceOf(IdentityProvisioningException.class)
                .satisfies(ex -> {
                    IdentityProvisioningException ipe = (IdentityProvisioningException) ex;
                    assertThat(ipe.isActionable()).isTrue();
                    assertThat(ipe.getCode()).isEqualTo("USER_IDP_NOT_INITIALIZED");
                });
        // setPassword failed → no audit, no save of the status flip.
        verify(audit, never()).record(any());
    }

    @Test
    void provisionFailurePropagatesAndRollsBack() {
        idpProps.setEnabled(true);
        UserJpaEntity user = user("jane@client.uz", "zid-1", UserStatus.ACTIVE);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(idp.provisionUser(eq("jane@client.uz"), any(), any(), eq(PW)))
                .thenThrow(new IdentityProvisioningException("upstream down"));

        assertThatThrownBy(() -> useCase.resetLogin(userId, PW))
                .isInstanceOf(IdentityProvisioningException.class);
        verify(idp, never()).setPassword(any(), any());
        verify(audit, never()).record(any());
    }
}
