package uz.hrlab.grading.integration.imports.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;

import java.util.UUID;

@Entity
@Table(name = "import_errors")
public class ImportErrorJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "import_batch_id", nullable = false, updatable = false)
    private UUID importBatchId;

    @Column(name = "import_batch_row_id")
    private UUID importBatchRowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_level", nullable = false, length = 16)
    private ImportErrorLevel errorLevel;

    @Column(name = "error_code", nullable = false, length = 64)
    private String errorCode;

    @Column(name = "field_name", length = 128)
    private String fieldName;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "suggested_fix", length = 1000)
    private String suggestedFix;

    @Column(name = "technical_details", length = 2000)
    private String technicalDetails;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected ImportErrorJpaEntity() { }

    public ImportErrorJpaEntity(UUID id, UUID tenantId, UUID importBatchId,
                                UUID importBatchRowId, ImportErrorLevel errorLevel,
                                String errorCode, String fieldName, String message,
                                String suggestedFix, String technicalDetails, String traceId) {
        this.id = id;
        this.tenantId = tenantId;
        this.importBatchId = importBatchId;
        this.importBatchRowId = importBatchRowId;
        this.errorLevel = errorLevel;
        this.errorCode = errorCode;
        this.fieldName = fieldName;
        this.message = message;
        this.suggestedFix = suggestedFix;
        this.technicalDetails = technicalDetails;
        this.traceId = traceId;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getImportBatchId() { return importBatchId; }
    public UUID getImportBatchRowId() { return importBatchRowId; }
    public ImportErrorLevel getErrorLevel() { return errorLevel; }
    public String getErrorCode() { return errorCode; }
    public String getFieldName() { return fieldName; }
    public String getMessage() { return message; }
    public String getSuggestedFix() { return suggestedFix; }
    public String getTechnicalDetails() { return technicalDetails; }
    public String getTraceId() { return traceId; }
}
