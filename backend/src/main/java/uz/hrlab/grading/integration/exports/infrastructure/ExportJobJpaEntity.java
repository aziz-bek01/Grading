package uz.hrlab.grading.integration.exports.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
public class ExportJobJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_type", nullable = false, length = 32)
    private ExportType exportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private ExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ExportJobStatus status;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "generated_at")
    private OffsetDateTime generatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "downloaded_at")
    private OffsetDateTime downloadedAt;

    @Column(name = "filter_params", columnDefinition = "TEXT")
    private String filterParams;

    @Column(name = "file_storage_key", length = 512)
    private String fileStorageKey;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_checksum", length = 128)
    private String fileChecksum;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "contains_salary_data", nullable = false)
    private boolean containsSalaryData;

    @Column(name = "contains_personal_data", nullable = false)
    private boolean containsPersonalData;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    // --- Batch-4 bounded-retry + dead-letter bookkeeping (migration 045) ---

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /** Earliest time the re-queuer may re-dispatch this row (exponential backoff). */
    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    protected ExportJobJpaEntity() { }

    public ExportJobJpaEntity(UUID id, UUID tenantId, UUID projectId,
                              ExportType exportType, ExportFormat format,
                              ExportJobStatus status, UUID requestedBy,
                              OffsetDateTime requestedAt, String filterParams,
                              boolean containsSalaryData, boolean containsPersonalData) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.exportType = exportType;
        this.format = format;
        this.status = status;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.filterParams = filterParams;
        this.containsSalaryData = containsSalaryData;
        this.containsPersonalData = containsPersonalData;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public ExportType getExportType() { return exportType; }
    public ExportFormat getFormat() { return format; }
    public ExportJobStatus getStatus() { return status; }
    public UUID getRequestedBy() { return requestedBy; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getDownloadedAt() { return downloadedAt; }
    public String getFilterParams() { return filterParams; }
    public String getFileStorageKey() { return fileStorageKey; }
    public Long getFileSize() { return fileSize; }
    public String getFileChecksum() { return fileChecksum; }
    public Integer getRowCount() { return rowCount; }
    public boolean isContainsSalaryData() { return containsSalaryData; }
    public boolean isContainsPersonalData() { return containsPersonalData; }
    public String getTraceId() { return traceId; }
    public int getAttemptCount() { return attemptCount; }
    public String getFailureReason() { return failureReason; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }

    public void setStatus(ExportJobStatus v) { this.status = v; }
    public void setGeneratedAt(OffsetDateTime v) { this.generatedAt = v; }
    public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
    public void setDownloadedAt(OffsetDateTime v) { this.downloadedAt = v; }
    public void setFileStorageKey(String v) { this.fileStorageKey = v; }
    public void setFileSize(Long v) { this.fileSize = v; }
    public void setFileChecksum(String v) { this.fileChecksum = v; }
    public void setRowCount(Integer v) { this.rowCount = v; }
    public void setTraceId(String v) { this.traceId = v; }
    public void incrementAttempt() { this.attemptCount++; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public void setNextAttemptAt(OffsetDateTime v) { this.nextAttemptAt = v; }
}
