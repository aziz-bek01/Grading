package uz.hrlab.grading.gradestructure.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Grade — one row inside a {@link GradeStructure} (e.g. "Grade 7"). */
public final class Grade {

    private final UUID id;
    private final UUID tenantId;
    private final UUID gradeStructureId;
    private final int gradeNumber;
    private final Map<String, String> nameI18n;
    private final Map<String, String> descriptionI18n;
    private final int sortOrder;

    public Grade(UUID id, UUID tenantId, UUID gradeStructureId,
                 int gradeNumber, Map<String, String> nameI18n,
                 Map<String, String> descriptionI18n, int sortOrder) {
        this.id = id;
        this.tenantId = tenantId;
        this.gradeStructureId = gradeStructureId;
        this.gradeNumber = gradeNumber;
        this.nameI18n = copy(nameI18n);
        this.descriptionI18n = copy(descriptionI18n);
        this.sortOrder = sortOrder;
    }

    private static Map<String, String> copy(Map<String, String> src) {
        return src == null ? new HashMap<>() : new HashMap<>(src);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID gradeStructureId() { return gradeStructureId; }
    public int gradeNumber() { return gradeNumber; }
    public Map<String, String> nameI18n() { return nameI18n; }
    public Map<String, String> descriptionI18n() { return descriptionI18n; }
    public int sortOrder() { return sortOrder; }
}
