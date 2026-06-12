package uz.hrlab.grading.evaluation.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.domain.EvaluationPanelStatus;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.tenancy.application.TenantContext;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-11 — bias-isolation read guard (R-CRIT-1 / REQ-ISO-1..6, DATA LAYER). While
 * a panel is collecting, a peer cannot read another evaluator's sheet; the owner
 * can; a {@code CAMPAIGN_RESULTS_VIEW} holder bypasses; {@code EVALUATION_READ}
 * alone does NOT lift the blind (deny-by-default); a peer attempt emits an
 * {@code ACCESS_DENIED_BY_ABAC} denial audit and a 404-equivalent (no reveal).
 */
@ExtendWith(MockitoExtension.class)
class PanelBiasGuardTest {

    @Mock PanelRepository panels;
    @Mock AuditService audit;

    PanelBiasGuard guard;

    UUID tenantId;
    UUID projectId;
    UUID panelId;
    UUID owner;
    UUID peer;

    @BeforeEach
    void setUp() {
        guard = new PanelBiasGuard(panels, audit);
        tenantId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        panelId = UUID.randomUUID();
        owner = UUID.randomUUID();
        peer = UUID.randomUUID();
    }

    @Test
    void ownerCanReadOwnSheetWhileCollecting() {
        stubCollectingPanel();
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);

        assertThatCode(() -> guard.enforceCanReadSheet(ctx(owner, Set.of("EVALUATION_READ")), sheet))
                .doesNotThrowAnyException();
        verify(audit, never()).record(any());
    }

    @Test
    void peerCannotReadForeignSheetWhileCollectingAndDenialIsAudited() {
        stubCollectingPanel();
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);

        assertThatThrownBy(() ->
                guard.enforceCanReadSheet(ctx(peer, Set.of("EVALUATION_READ")), sheet))
                .isInstanceOf(TenantAccessDeniedException.class); // -> 404, no reveal

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(captor.capture());
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.ACCESS_DENIED_BY_ABAC);
        assertThat(captor.getValue().reason()).contains("PanelBiasGuard");
    }

    @Test
    void evaluationReadAloneDoesNotLiftTheBlind() {
        stubCollectingPanel();
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);
        // Peer with only EVALUATION_READ (no CAMPAIGN_RESULTS_VIEW) is still blocked.
        assertThatThrownBy(() ->
                guard.enforceCanReadSheet(ctx(peer, Set.of("EVALUATION_READ")), sheet))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void campaignResultsViewHolderBypassesTheBlind() {
        // Panel repo must NOT even be consulted — bypass short-circuits first.
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);

        assertThatCode(() -> guard.enforceCanReadSheet(
                ctx(peer, Set.of("EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW")), sheet))
                .doesNotThrowAnyException();
        verify(panels, never()).findByIdAndTenantId(any(), any());
        verify(audit, never()).record(any());
    }

    @Test
    void panellessLegacySheetHasNoBlind() {
        EvaluationJpaEntity sheet = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                owner, EvaluationStatus.DRAFT); // panel_id stays null
        assertThatCode(() -> guard.enforceCanReadSheet(ctx(peer, Set.of("EVALUATION_READ")), sheet))
                .doesNotThrowAnyException();
        verify(panels, never()).findByIdAndTenantId(any(), any());
    }

    @Test
    void peerCanReadForeignSheetOncePanelPastCollecting() {
        // AVERAGED panel — the collecting window is over; the single-id guard no
        // longer confines (result-level gating handles AVERAGED visibility, BE-13).
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                EvaluationPanelStatus.AVERAGED, 3);
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));
        EvaluationJpaEntity sheet = sheetOwnedBy(owner);

        assertThatCode(() -> guard.enforceCanReadSheet(ctx(peer, Set.of("EVALUATION_READ")), sheet))
                .doesNotThrowAnyException();
        verify(audit, never()).record(any());
    }

    @Test
    void shouldConfineGridToOwnIsTrueForNonBypassCaller() {
        assertThat(guard.shouldConfineGridToOwn(ctx(peer, Set.of("EVALUATION_READ")))).isTrue();
    }

    @Test
    void shouldConfineGridToOwnIsFalseForBypassHolder() {
        assertThat(guard.shouldConfineGridToOwn(
                ctx(peer, Set.of("EVALUATION_READ", "CAMPAIGN_RESULTS_VIEW")))).isFalse();
    }

    // --------------------------------------------------------------- helpers

    private void stubCollectingPanel() {
        EvaluationPanelJpaEntity panel = new EvaluationPanelJpaEntity(
                panelId, tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                EvaluationPanelStatus.AWAITING_EVALUATIONS, 3);
        when(panels.findByIdAndTenantId(panelId, tenantId)).thenReturn(Optional.of(panel));
    }

    private EvaluationJpaEntity sheetOwnedBy(UUID evaluator) {
        EvaluationJpaEntity sheet = new EvaluationJpaEntity(
                UUID.randomUUID(), tenantId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                evaluator, EvaluationStatus.INCOMPLETE);
        sheet.setPanelId(panelId);
        return sheet;
    }

    private TenantContext ctx(UUID userId, Set<String> permissions) {
        return new TenantContext(userId, tenantId, Set.of(projectId), Set.of(),
                permissions, Set.of(), false, "ru-RU");
    }
}
