package uz.hrlab.grading.reporting.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportStatus;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA mapping of the {@code reports} table (architecture §17 / ADR-009 async
 * report generation).
 *
 * <p>{@code containsSalaryData} is always {@code false} in MVP 2 — the column
 * is present so the salary-bearing column-level RLS rules (security-blueprint
 * §9) light up in MVP 3 without a migration.
 */
@Entity
@Table(name = "reports")
public class ReportJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 32)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private ReportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ReportStatus status;

    @Column(name = "title", nullable = false, length = 256)
    private String title;

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

    @Column(name = "locale", length = 16)
    private String locale;

    @Column(name = "contains_salary_data", nullable = false)
    private boolean containsSalaryData;

    @Column(name = "contains_personal_data", nullable = false)
    private boolean containsPersonalData;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    /** Batch-4: earliest time the re-queuer may re-dispatch this row (backoff). */
    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected ReportJpaEntity() { }

    public ReportJpaEntity(UUID id, UUID tenantId, UUID projectId,
                           ReportType reportType, ReportFormat format,
                           ReportStatus status, String title,
                           UUID requestedBy, OffsetDateTime requestedAt,
                           String filterParams, String locale,
                           boolean containsSalaryData, boolean containsPersonalData) {
        this.id = id;
        this.tenantId = tenantId;
        this.projectId = projectId;
        this.reportType = reportType;
        this.format = format;
        this.status = status;
        this.title = title;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
        this.filterParams = filterParams;
        this.locale = locale;
        this.containsSalaryData = containsSalaryData;
        this.containsPersonalData = containsPersonalData;
        this.attemptCount = 0;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getProjectId() { return projectId; }
    public ReportType getReportType() { return reportType; }
    public ReportFormat getFormat() { return format; }
    public ReportStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public UUID getRequestedBy() { return requestedBy; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getDownloadedAt() { return downloadedAt; }
    public String getFilterParams() { return filterParams; }
    public String getFileStorageKey() { return fileStorageKey; }
    public Long getFileSize() { return fileSize; }
    public String getFileChecksum() { return fileChecksum; }
    public String getLocale() { return locale; }
    public boolean isContainsSalaryData() { return containsSalaryData; }
    public boolean isContainsPersonalData() { return containsPersonalData; }
    public int getAttemptCount() { return attemptCount; }
    public String getFailureReason() { return failureReason; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getTraceId() { return traceId; }

    public void setStatus(ReportStatus v) { this.status = v; }
    public void setGeneratedAt(OffsetDateTime v) { this.generatedAt = v; }
    public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
    public void setDownloadedAt(OffsetDateTime v) { this.downloadedAt = v; }
    public void setFileStorageKey(String v) { this.fileStorageKey = v; }
    public void setFileSize(Long v) { this.fileSize = v; }
    public void setFileChecksum(String v) { this.fileChecksum = v; }
    public void incrementAttempt() { this.attemptCount++; }
    public void setFailureReason(String v) { this.failureReason = v; }
    public void setNextAttemptAt(OffsetDateTime v) { this.nextAttemptAt = v; }
    public void setTraceId(String v) { this.traceId = v; }
}
