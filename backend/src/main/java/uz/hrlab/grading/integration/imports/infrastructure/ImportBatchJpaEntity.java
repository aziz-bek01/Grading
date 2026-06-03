package uz.hrlab.grading.integration.imports.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.integration.imports.domain.ImportBatchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "import_batches")
public class ImportBatchJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "template_code", nullable = false, length = 64, updatable = false)
    private String templateCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ImportBatchStatus status;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "file_storage_key", length = 512)
    private String fileStorageKey;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_checksum", length = 128)
    private String fileChecksum;

    @Column(name = "total_row_count")
    private Integer totalRowCount;

    @Column(name = "error_row_count")
    private Integer errorRowCount;

    @Column(name = "warning_row_count")
    private Integer warningRowCount;

    @Column(name = "committed_row_count")
    private Integer committedRowCount;

    @Column(name = "contains_salary_data", nullable = false)
    private boolean containsSalaryData;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "committed_by")
    private UUID committedBy;

    @Column(name = "committed_at")
    private OffsetDateTime committedAt;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected ImportBatchJpaEntity() { }

    public ImportBatchJpaEntity(UUID id, UUID tenantId, UUID projectId, String templateCode,
                                ImportBatchStatus status, String originalFilename,
                                String fileStorageKey, Long fileSize, String fileChecksum,
                                UUID uploadedBy, OffsetDateTime uploadedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.templateCode = templateCode;
        this.status = status;
        this.originalFilename = originalFilename;
        this.fileStorageKey = fileStorageKey;
        this.fileSize = fileSize;
        this.fileChecksum = fileChecksum;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.containsSalaryData = false;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public String getTemplateCode() { return templateCode; }
    public ImportBatchStatus getStatus() { return status; }
    public String getOriginalFilename() { return originalFilename; }
    public String getFileStorageKey() { return fileStorageKey; }
    public Long getFileSize() { return fileSize; }
    public String getFileChecksum() { return fileChecksum; }
    public Integer getTotalRowCount() { return totalRowCount; }
    public Integer getErrorRowCount() { return errorRowCount; }
    public Integer getWarningRowCount() { return warningRowCount; }
    public Integer getCommittedRowCount() { return committedRowCount; }
    public boolean isContainsSalaryData() { return containsSalaryData; }
    public UUID getUploadedBy() { return uploadedBy; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public UUID getCommittedBy() { return committedBy; }
    public OffsetDateTime getCommittedAt() { return committedAt; }
    public String getTraceId() { return traceId; }

    public void setStatus(ImportBatchStatus status) { this.status = status; }
    public void setTotalRowCount(Integer v) { this.totalRowCount = v; }
    public void setErrorRowCount(Integer v) { this.errorRowCount = v; }
    public void setWarningRowCount(Integer v) { this.warningRowCount = v; }
    public void setCommittedRowCount(Integer v) { this.committedRowCount = v; }
    public void setContainsSalaryData(boolean v) { this.containsSalaryData = v; }
    public void setCommittedBy(UUID v) { this.committedBy = v; }
    public void setCommittedAt(OffsetDateTime v) { this.committedAt = v; }
    public void setTraceId(String v) { this.traceId = v; }
}
