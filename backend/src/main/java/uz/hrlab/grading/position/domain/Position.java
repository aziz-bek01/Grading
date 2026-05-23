package uz.hrlab.grading.position.domain;

import java.util.Map;
import java.util.UUID;

public final class Position {

    private final UUID id;
    private final UUID tenantId;
    private final UUID projectId;
    private final UUID departmentId;
    private final String code;
    private final Map<String, String> titleI18n;
    private final String function;
    private final String category;
    private final String jobFamily;
    private final String jobLevel;
    private final PositionStatus status;

    public Position(UUID id, UUID tenantId, UUID projectId, UUID departmentId, String code,
                    Map<String, String> titleI18n, String function, String category,
                    String jobFamily, String jobLevel, PositionStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.departmentId = departmentId;
        this.code = code;
        this.titleI18n = titleI18n;
        this.function = function;
        this.category = category;
        this.jobFamily = jobFamily;
        this.jobLevel = jobLevel;
        this.status = status;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public UUID departmentId() { return departmentId; }
    public String code() { return code; }
    public Map<String, String> titleI18n() { return titleI18n; }
    public String function() { return function; }
    public String category() { return category; }
    public String jobFamily() { return jobFamily; }
    public String jobLevel() { return jobLevel; }
    public PositionStatus status() { return status; }
}
