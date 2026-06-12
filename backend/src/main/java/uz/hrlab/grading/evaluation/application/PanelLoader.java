package uz.hrlab.grading.evaluation.application;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationPanelJpaEntity;
import uz.hrlab.grading.evaluation.infrastructure.PanelRepository;
import uz.hrlab.grading.position.infrastructure.PositionJpaEntity;
import uz.hrlab.grading.position.infrastructure.PositionRepository;

import java.util.UUID;

/**
 * Loads a panel + its position tenant-scoped in one place so every panel use
 * case enforces {@code findByIdAndTenantId} uniformly (anti-BOLA). The position
 * is needed for the ABAC department gate (a panel inherits its department from
 * its position, exactly like an evaluation).
 */
@Component
public class PanelLoader {

    private final PanelRepository panels;
    private final PositionRepository positions;

    public PanelLoader(PanelRepository panels, PositionRepository positions) {
        this.panels = panels;
        this.positions = positions;
    }

    public EvaluationPanelJpaEntity requirePanel(UUID panelId, UUID tenantId) {
        return panels.findByIdAndTenantId(panelId, tenantId)
                .orElseThrow(TenantAccessDeniedException::new);
    }

    public PositionJpaEntity requirePosition(EvaluationPanelJpaEntity panel, UUID tenantId) {
        return positions.findByIdAndTenantId(panel.getPositionId(), tenantId)
                .orElseThrow(TenantAccessDeniedException::new);
    }
}
