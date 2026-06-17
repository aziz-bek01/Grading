package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.access.application.RoleCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * BE-11 / Defect-2 — bias-isolation read guard (R-CRIT-1 / REQ-ISO-1..6, DATA
 * LAYER). A per-evaluator PANEL sheet is readable ONLY by its OWNING evaluator or
 * an HRLab OVERSIGHT role ({@link RoleCodes#PANEL_OVERSIGHT_ROLES}). A peer cannot
 * read another evaluator's sheet — even if the peer holds
 * {@code CAMPAIGN_RESULTS_VIEW} (Defect-2: that permission no longer bypasses the
 * per-sheet blind). The blind is independent of panel phase. A peer attempt emits
 * an {@code ACCESS_DENIED_BY_ABAC} denial audit and a 404-equivalent (no reveal).
 */
@ExtendWith(MockitoExtension.class)
class PanelBiasGuardTest {

    @Mock AuditService audit;

    PanelBiasGuard guard;

    UUID tenantId;
    UUID projectId;
    UUID panelId;
    UUID owner;
    UUID peer;

    @BeforeEach
    void setUp() {
        guard = new PanelBiasGuard(audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        owner = UUID.randomUUID();
        peer = UUID.randomUUID();
    }

    @Test
    void ownerCanReadOwnPanelSheet() {
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);

        assertThatCode(() -> guard.enforceCanReadSheet(
                ctx(owner, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ"), sheet))
                .doesNotThrowAnyException();
        verify(audit, never()).record(any());
    }

    @Test
    void peerCannotReadForeignPanelSheetAndDenialIsAudited() {
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);

        assertThatThrownBy(() -> guard.enforceCanReadSheet(
                ctx(peer, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ"), sheet))
                .isInstanceOf(TenantAccessDeniedException.class); // -> 404, no reveal

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.ACCESS_DENIED_BY_ABAC);
        assertThat(captor.getValue().reason()).contains("PanelBiasGuard");
    }

    @Test
    void evaluationReadAloneDoesNotLiftTheBlind() {
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);
        // Peer with only EVALUATION_READ is still blocked.
        assertThatThrownBy(() -> guard.enforceCanReadSheet(
                ctx(peer, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ"), sheet))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void campaignResultsViewPeerStillCannotReadForeignPanelSheet() {
        // Defect-2: CAMPAIGN_RESULTS_VIEW (held e.g. by a department-director peer
        // role) must NOT bypass the per-evaluator blind. The peer is still 404'd.
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);

        assertThatThrownBy(() -> guard.enforceCanReadSheet(
                ctx(peer, Set.of(RoleCodes.DEPARTMENT_MANAGER),
                        "EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW"), sheet))
                .isInstanceOf(TenantAccessDeniedException.class);
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.ACCESS_DENIED_BY_ABAC);
    }

    @Test
    void hrlabOversightRoleBypassesTheBlind() {
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);

        // Each oversight role may read a peer's panel sheet for calibration.
        for (String role : RoleCodes.PANEL_OVERSIGHT_ROLES) {
            assertThatCode(() -> guard.enforceCanReadSheet(
                    ctx(peer, Set.of(role), "EVALUATION_READ"), sheet))
                    .doesNotThrowAnyException();
        }
        verify(audit, never()).record(any());
    }

    @Test
    void panellessLegacySheetHasNoBlind() {
        EvaluationJpaEntity sheet = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                owner, EvaluationStatus.DRAFT); // panel_id stays null
        assertThatCode(() -> guard.enforceCanReadSheet(
                ctx(peer, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ"), sheet))
                .doesNotThrowAnyException();
        verify(audit, never()).record(any());
    }

    @Test
    void shouldConfineGridToOwnIsTrueForNonOversightCaller() {
        assertThat(guard.shouldConfineGridToOwn(
                ctx(peer, Set.of(RoleCodes.EVALUATION_COMMITTEE_MEMBER), "EVALUATION_READ"))).isTrue();
    }

    @Test
    void shouldConfineGridToOwnIsTrueEvenForCampaignResultsViewPeer() {
        // Defect-2: CAMPAIGN_RESULTS_VIEW no longer lifts the grid blind either.
        assertThat(guard.shouldConfineGridToOwn(
                ctx(peer, Set.of(RoleCodes.DEPARTMENT_MANAGER),
                        "EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW"))).isTrue();
    }

    @Test
    void shouldConfineGridToOwnIsFalseForOversightRole() {
        assertThat(guard.shouldConfineGridToOwn(
                ctx(peer, Set.of(RoleCodes.HRLAB_CONSULTANT), "EVALUATION_READ"))).isFalse();
    }

    // --------------------------------------------------------------- helpers

    private EvaluationJpaEntity sheetOwnedBy(UUID evaluator) {
        EvaluationJpaEntity sheet = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                evaluator, EvaluationStatus.INCOMPLETE);
        sheet.setPanelId(panelId);
        return sheet;
    }

    private TenantContext ctx(UUID userId, Set<String> roles, String... permissions) {
        return new TenantContext(userId, tenantId, Set.of(projectId), roles,
                Set.of(permissions), Set.of(), false, "ru-RU");
    }
}
