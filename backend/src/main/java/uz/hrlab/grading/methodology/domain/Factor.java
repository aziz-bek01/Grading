package uz.hrlab.grading.methodology.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Factor — belongs to a {@link MethodologyVersion}. */
public final class Factor {

    private final UUID id;
    private final UUID tenantId;
    private final UUID methodologyVersionId;
    private final String code;
    private final Map<String, String> nameI18n;
    private final Map<String, String> descriptionI18n;
    private final BigDecimal weight;
    private final BigDecimal maxPoints;
    private final int sortOrder;
    private final boolean required;

    public Factor(UUID id, UUID tenantId, UUID methodologyVersionId, String code,
                  Map<String, String> nameI18n, Map<String, String> descriptionI18n,
                  BigDecimal weight, BigDecimal maxPoints, int sortOrder, boolean required) {
        this.id = id;
        this.tenantId = tenantId;
        this.methodologyVersionId = methodologyVersionId;
        this.code = code;
        this.nameI18n = copy(nameI18n);
        this.descriptionI18n = copy(descriptionI18n);
        this.weight = weight;
        this.maxPoints = maxPoints;
        this.sortOrder = sortOrder;
        this.required = required;
    }

    private static Map<String, String> copy(Map<String, String> src) {
        return src == null ? new HashMap<>() : new HashMap<>(src);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID methodologyVersionId() { return methodologyVersionId; }
    public String code() { return code; }
    public Map<String, String> nameI18n() { return nameI18n; }
    public Map<String, String> descriptionI18n() { return descriptionI18n; }
    public BigDecimal weight() { return weight; }
    public BigDecimal maxPoints() { return maxPoints; }
    public int sortOrder() { return sortOrder; }
    public boolean required() { return required; }
}
