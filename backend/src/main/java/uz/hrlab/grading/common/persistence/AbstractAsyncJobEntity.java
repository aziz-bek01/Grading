package uz.hrlab.grading.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.OffsetDateTime;

/**
 * Shared lifecycle / artefact / retry columns for the async job-bearing tables
 * ({@code export_jobs}, {@code reports}). These columns are byte-identical in
 * name, type and definition across both Liquibase-created tables, so they live
 * on one {@code @MappedSuperclass} (database-blueprint §5; ADR-009 async export
 * + report generation share the same dead-letter / backoff bookkeeping).
 *
 * <p>Deliberately NOT moved here because they differ per table:
 * <ul>
 *   <li>{@code status} / {@code format} / {@code *_type} — distinct enum types
 *       per job kind ({@code ExportJobStatus} vs {@code ReportStatus}, etc.);</li>
 *   <li>{@code failure_reason} — {@code TEXT} on {@code export_jobs} but
 *       {@code varchar(512)} on {@code reports};</li>
 *   <li>{@code id} / {@code tenant_id} / {@code project_id} / {@code requested_by}
 *       / {@code requested_at} — identity + ownership columns each subclass
 *       declares itself (same convention as {@link AuditedJpaEntity}).</li>
 * </ul>
 *
 * <p>This is a pure refactor: Hibernate maps these columns by name to the
 * existing tables; no Liquibase changeset, column rename, or nullability change
 * is involved.
 */
@MappedSuperclass
public abstract class AbstractAsyncJobEntity extends AuditedJpaEntity {

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

    @Column(name = "contains_salary_data", nullable = false)
    private boolean containsSalaryData;

    @Column(name = "contains_personal_data", nullable = false)
    private boolean containsPersonalData;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /** Earliest time the re-queuer may re-dispatch this row (exponential backoff). */
    @Column(name = "next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getDownloadedAt() { return downloadedAt; }
    public String getFilterParams() { return filterParams; }
    public String getFileStorageKey() { return fileStorageKey; }
    public Long getFileSize() { return fileSize; }
    public String getFileChecksum() { return fileChecksum; }
    public boolean isContainsSalaryData() { return containsSalaryData; }
    public boolean isContainsPersonalData() { return containsPersonalData; }
    public int getAttemptCount() { return attemptCount; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getTraceId() { return traceId; }

    public void setGeneratedAt(OffsetDateTime v) { this.generatedAt = v; }
    public void setExpiresAt(OffsetDateTime v) { this.expiresAt = v; }
    public void setDownloadedAt(OffsetDateTime v) { this.downloadedAt = v; }
    public void setFileStorageKey(String v) { this.fileStorageKey = v; }
    public void setFileSize(Long v) { this.fileSize = v; }
    public void setFileChecksum(String v) { this.fileChecksum = v; }
    public void incrementAttempt() { this.attemptCount++; }
    public void setNextAttemptAt(OffsetDateTime v) { this.nextAttemptAt = v; }
    public void setTraceId(String v) { this.traceId = v; }

    /** Subclasses set the shared file/flag fields during construction. */
    protected void initFilterParams(String filterParams) { this.filterParams = filterParams; }
    protected void initContainsSalaryData(boolean v) { this.containsSalaryData = v; }
    protected void initContainsPersonalData(boolean v) { this.containsPersonalData = v; }
}
