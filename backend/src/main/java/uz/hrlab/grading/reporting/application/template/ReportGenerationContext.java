package uz.hrlab.grading.reporting.application.template;

import uz.hrlab.grading.reporting.domain.ReportFormat;
import uz.hrlab.grading.reporting.domain.ReportType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Carrier for everything a {@link ReportTemplate} needs to render its content.
 *
 * <p>Workers populate this from the reloaded {@code ReportJpaEntity} + active
 * {@code TenantContext} so the template stays decoupled from JPA, the worker
 * pool, and the security context.
 */
public final class ReportGenerationContext {

    private final UUID reportId;
    private final UUID tenantId;
    private final UUID projectId;
    private final ReportType reportType;
    private final ReportFormat format;
    private final String locale;
    private final String filterParams;
    private final UUID requestedBy;
    private final OffsetDateTime requestedAt;
    private final String title;

    private ReportGenerationContext(Builder b) {
        this.reportId = b.reportId;
        this.tenantId = b.tenantId;
        this.projectId = b.projectId;
        this.reportType = b.reportType;
        this.format = b.format;
        this.locale = b.locale == null ? "ru-RU" : b.locale;
        this.filterParams = b.filterParams;
        this.requestedBy = b.requestedBy;
        this.requestedAt = b.requestedAt;
        this.title = b.title;
    }

    public static Builder builder() { return new Builder(); }

    public UUID reportId() { return reportId; }
    public UUID tenantId() { return tenantId; }
    public UUID projectId() { return projectId; }
    public ReportType reportType() { return reportType; }
    public ReportFormat format() { return format; }
    public String locale() { return locale; }
    public String filterParams() { return filterParams; }
    public UUID requestedBy() { return requestedBy; }
    public OffsetDateTime requestedAt() { return requestedAt; }
    public String title() { return title; }

    public static final class Builder {
        private UUID reportId;
        private UUID tenantId;
        private UUID projectId;
        private ReportType reportType;
        private ReportFormat format;
        private String locale;
        private String filterParams;
        private UUID requestedBy;
        private OffsetDateTime requestedAt;
        private String title;

        public Builder reportId(UUID v) { this.reportId = v; return this; }
        public Builder tenantId(UUID v) { this.tenantId = v; return this; }
        public Builder projectId(UUID v) { this.projectId = v; return this; }
        public Builder reportType(ReportType v) { this.reportType = v; return this; }
        public Builder format(ReportFormat v) { this.format = v; return this; }
        public Builder locale(String v) { this.locale = v; return this; }
        public Builder filterParams(String v) { this.filterParams = v; return this; }
        public Builder requestedBy(UUID v) { this.requestedBy = v; return this; }
        public Builder requestedAt(OffsetDateTime v) { this.requestedAt = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public ReportGenerationContext build() { return new ReportGenerationContext(this); }
    }
}
