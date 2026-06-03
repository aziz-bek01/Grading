package uz.hrlab.grading.methodology.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** FactorLevel — belongs to a {@link Factor}. */
public final class FactorLevel {

    private final UUID id;
    private final UUID tenantId;
    private final UUID factorId;
    private final String code;
    private final int levelOrder;
    private final BigDecimal points;
    private final BigDecimal scaleValue;
    private final Map<String, String> labelI18n;
    private final Map<String, String> descriptionI18n;

    public FactorLevel(UUID id, UUID tenantId, UUID factorId, String code, int levelOrder,
                       BigDecimal points, BigDecimal scaleValue,
                       Map<String, String> labelI18n, Map<String, String> descriptionI18n) {
        this.id = id;
        this.tenantId = tenantId;
        this.factorId = factorId;
        this.code = code;
        this.levelOrder = levelOrder;
        this.points = points;
        this.scaleValue = scaleValue;
        this.labelI18n = copy(labelI18n);
        this.descriptionI18n = copy(descriptionI18n);
    }

    private static Map<String, String> copy(Map<String, String> src) {
        return src == null ? new HashMap<>() : new HashMap<>(src);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID factorId() { return factorId; }
    public String code() { return code; }
    public int levelOrder() { return levelOrder; }
    public BigDecimal points() { return points; }
    public BigDecimal scaleValue() { return scaleValue; }
    public Map<String, String> labelI18n() { return labelI18n; }
    public Map<String, String> descriptionI18n() { return descriptionI18n; }
}
