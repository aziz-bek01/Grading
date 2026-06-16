package uz.hrlab.grading.integration.exports.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AbstractAsyncJobEntity;
import uz.hrlab.grading.integration.exports.domain.ExportFormat;
import uz.hrlab.grading.integration.exports.domain.ExportJobStatus;
import uz.hrlab.grading.integration.exports.domain.ExportType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
public class ExportJobJpaEntity extends AbstractAsyncJobEntity {

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

    @Column(name = "row_count")
    private Integer rowCount;

    // --- Batch-4 bounded-retry + dead-letter bookkeeping (migration 045) ---
    // Shared lifecycle/file/retry columns (generated_at, expires_at, downloaded_at,
    // filter_params, file_storage_key, file_size, file_checksum, contains_salary_data,
    // contains_personal_data, attempt_count, next_attempt_at, trace_id) live on
    // AbstractAsyncJobEntity. failure_reason stays here: export_jobs maps it to TEXT.

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

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
        initFilterParams(filterParams);
        initContainsSalaryData(containsSalaryData);
        initContainsPersonalData(containsPersonalData);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public ExportType getExportType() { return exportType; }
    public ExportFormat getFormat() { return format; }
    public ExportJobStatus getStatus() { return status; }
    public UUID getRequestedBy() { return requestedBy; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public Integer getRowCount() { return rowCount; }
    public String getFailureReason() { return failureReason; }

    public void setStatus(ExportJobStatus v) { this.status = v; }
    public void setRowCount(Integer v) { this.rowCount = v; }
    public void setFailureReason(String v) { this.failureReason = v; }
}
