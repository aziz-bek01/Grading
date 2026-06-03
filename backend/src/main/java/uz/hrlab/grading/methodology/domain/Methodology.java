package uz.hrlab.grading.methodology.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Methodology domain model — the long-lived container. State machine lives on
 * {@link MethodologyVersion}; the container itself is just ACTIVE/ARCHIVED.
 *
 * <p>{@code projectId} = {@code null} marks a global HRLab template visible
 * tenant-wide; non-null = project-scoped instance.
 */
public final class Methodology {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final String code;
    private final Map<String, String> nameI18n;
    private final Map<String, String> descriptionI18n;
    private final MethodologyType methodologyType;
    private final MethodologyStatus status;

    public Methodology(UUID id, UUID tenantId, UUID projectId, String code,
                       Map<String, String> nameI18n,
                       Map<String, String> descriptionI18n,
                       MethodologyType methodologyType,
                       MethodologyStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.code = code;
        this.nameI18n = copy(nameI18n);
        this.descriptionI18n = copy(descriptionI18n);
        this.methodologyType = methodologyType;
        this.status = status;
    }

    private static Map<String, String> copy(Map<String, String> src) {
        return src == null ? new HashMap<>() : new HashMap<>(src);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public String code() { return code; }
    public Map<String, String> nameI18n() { return nameI18n; }
    public Map<String, String> descriptionI18n() { return descriptionI18n; }
    public MethodologyType methodologyType() { return methodologyType; }
    public MethodologyStatus status() { return status; }
}
