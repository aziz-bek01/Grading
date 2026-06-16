package uz.hrlab.grading.reporting.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import uz.hrlab.grading.common.persistence.AbstractAsyncJobEntity;
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
public class ReportJpaEntity extends AbstractAsyncJobEntity {

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

    @Column(name = "locale", length = 16)
    private String locale;

    // Shared lifecycle/file/retry columns (generated_at, expires_at, downloaded_at,
    // filter_params, file_storage_key, file_size, file_checksum, contains_salary_data,
    // contains_personal_data, attempt_count, next_attempt_at, trace_id) live on
    // AbstractAsyncJobEntity. failure_reason stays here: reports maps it to varchar(512).

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

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
        this.locale = locale;
        initFilterParams(filterParams);
        initContainsSalaryData(containsSalaryData);
        initContainsPersonalData(containsPersonalData);
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
    public String getLocale() { return locale; }
    public String getFailureReason() { return failureReason; }

    public void setStatus(ReportStatus v) { this.status = v; }
    public void setFailureReason(String v) { this.failureReason = v; }
}
