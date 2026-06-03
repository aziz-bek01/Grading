package uz.hrlab.grading.integration.imports.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.integration.imports.domain.ImportBatchRowStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_batch_rows")
public class ImportBatchRowJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "import_batch_id", nullable = false, updatable = false)
    private UUID importBatchId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "parsed_json", columnDefinition = "TEXT")
    private String parsedJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImportBatchRowStatus status;

    @Column(name = "target_entity_type", length = 64)
    private String targetEntityType;

    @Column(name = "target_entity_id")
    private UUID targetEntityId;

    @Column(name = "committed_at")
    private OffsetDateTime committedAt;

    protected ImportBatchRowJpaEntity() { }

    public ImportBatchRowJpaEntity(UUID id, UUID tenantId, UUID importBatchId,
                                   int rowNumber, String rawJson, String parsedJson,
                                   ImportBatchRowStatus status) {
        this.id = id;
        this.tenantId = tenantId;
        this.importBatchId = importBatchId;
        this.rowNumber = rowNumber;
        this.rawJson = rawJson;
        this.parsedJson = parsedJson;
        this.status = status;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getImportBatchId() { return importBatchId; }
    public int getRowNumber() { return rowNumber; }
    public String getRawJson() { return rawJson; }
    public String getParsedJson() { return parsedJson; }
    public ImportBatchRowStatus getStatus() { return status; }
    public String getTargetEntityType() { return targetEntityType; }
    public UUID getTargetEntityId() { return targetEntityId; }
    public OffsetDateTime getCommittedAt() { return committedAt; }

    public void setStatus(ImportBatchRowStatus v) { this.status = v; }
    public void setParsedJson(String v) { this.parsedJson = v; }
    public void setTargetEntityType(String v) { this.targetEntityType = v; }
    public void setTargetEntityId(UUID v) { this.targetEntityId = v; }
    public void setCommittedAt(OffsetDateTime v) { this.committedAt = v; }
}
