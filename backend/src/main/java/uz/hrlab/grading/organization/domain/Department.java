package uz.hrlab.grading.organization.domain;

import java.util.Map;
import java.util.UUID;

/**
 * Pure domain model for a Department (architecture §9 row "Department").
 *
 * <p>Tree built externally via {@link DepartmentTreeBuilder}. Cycle prevention
 * lives in {@link DepartmentValidationPolicy}.
 */
public final class Department {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final UUID parentId;
    private final String code;
    private final Map<String, String> nameI18n;
    private final DepartmentType type;
    private final DepartmentStatus status;

    public Department(UUID id, UUID tenantId, UUID projectId, UUID parentId, String code,
                      Map<String, String> nameI18n, DepartmentType type, DepartmentStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.parentId = parentId;
        this.code = code;
        this.nameI18n = nameI18n;
        this.type = type;
        this.status = status;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public UUID parentId() { return parentId; }
    public String code() { return code; }
    public Map<String, String> nameI18n() { return nameI18n; }
    public DepartmentType type() { return type; }
    public DepartmentStatus status() { return status; }
}
