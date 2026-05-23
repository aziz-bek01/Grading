package uz.hrlab.grading.project.domain;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Pure domain model for a grading Project (architecture §9 row "Project").
 *
 * <p>Multilingual {@code nameI18n} stored as JSONB Map&lt;locale, value&gt;.
 * Methodology version reference is nullable in Phase 2 (used by Phase 4+).
 */
public final class Project {

    private final UUID id;
    private final UUID tenantId;
    private final String code;
    private final Map<String, String> nameI18n;
    private final String description;
    private final ProjectStatus status;
    private final UUID methodologyVersionId;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public Project(UUID id, UUID tenantId, String code, Map<String, String> nameI18n,
                   String description, ProjectStatus status, UUID methodologyVersionId,
                   LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.nameI18n = nameI18n;
        this.description = description;
        this.status = status;
        this.methodologyVersionId = methodologyVersionId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String code() { return code; }
    public Map<String, String> nameI18n() { return nameI18n; }
    public String description() { return description; }
    public ProjectStatus status() { return status; }
    public UUID methodologyVersionId() { return methodologyVersionId; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
}
