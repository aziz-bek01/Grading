package uz.hrlab.grading.evaluation.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable snapshot of one factor's averaged score across a panel's evaluators. */
public record PanelFactorAverage(
        UUID id,
        UUID tenantId,
        UUID panelId,
        UUID factorId,
        BigDecimal avgRawFactorScore,
        int evaluatorCount
) { }
