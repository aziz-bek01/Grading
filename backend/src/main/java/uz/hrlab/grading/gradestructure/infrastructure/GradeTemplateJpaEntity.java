package uz.hrlab.grading.gradestructure.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.gradestructure.domain.GradeStructureType;
import uz.hrlab.grading.gradestructure.domain.GradeTemplateStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tenant-defined (DB-backed) CUSTOM grade template (BE-7) — backs
 * {@code grade_templates} (033-create-grade-templates.yaml). EXACT mirror of
 * {@link uz.hrlab.grading.methodology.infrastructure.MethodologyTemplateJpaEntity}.
 *
 * <p>Holds a FROZEN deep copy of a grade structure's grades + bands
 * ({@link #gradesSnapshot}, a jsonb column) so
 * {@code CreateGradeStructureFromTemplate} can re-mint a brand-new DRAFT
 * structure reproducibly.
 *
 * <p>Tenant-scoped: {@code tenant_id} is set from the active {@code TenantContext}
 * on write (never the request body); RLS on the table is defense-in-depth.
 */
@Entity
@Table(name = "grade_templates")
public class GradeTemplateJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, length = 64, updatable = false)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", columnDefinition = "jsonb", nullable = false)
    private Map<String, String> nameI18n = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "jsonb")
    private Map<String, String> descriptionI18n = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "structure_type", nullable = false, length = 32, updatable = false)
    private GradeStructureType structureType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grades_snapshot", columnDefinition = "jsonb", nullable = false, updatable = false)
    private GradeTemplateSnapshot gradesSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GradeTemplateStatus status = GradeTemplateStatus.ACTIVE;

    protected GradeTemplateJpaEntity() { }

    public GradeTemplateJpaEntity(UUID id, UUID tenantId, String code,
                                  GradeStructureType structureType,
                                  GradeTemplateSnapshot gradesSnapshot,
                                  GradeTemplateStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.structureType = structureType;
        this.gradesSnapshot = gradesSnapshot;
        this.status = status == null ? GradeTemplateStatus.ACTIVE : status;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public Map<String, String> getNameI18n() { return nameI18n; }
    public Map<String, String> getDescriptionI18n() { return descriptionI18n; }
    public GradeStructureType getStructureType() { return structureType; }
    public GradeTemplateSnapshot getGradesSnapshot() { return gradesSnapshot; }
    public GradeTemplateStatus getStatus() { return status; }

    public void setNameI18n(Map<String, String> v) { this.nameI18n = nullSafe(v); }
    public void setDescriptionI18n(Map<String, String> v) { this.descriptionI18n = nullSafe(v); }
    public void setStatus(GradeTemplateStatus v) { this.status = v; }

    private static Map<String, String> nullSafe(Map<String, String> in) {
        return in == null ? new HashMap<>() : new HashMap<>(in);
    }
}
