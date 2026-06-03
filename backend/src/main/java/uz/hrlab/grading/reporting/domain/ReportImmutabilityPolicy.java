package uz.hrlab.grading.reporting.domain;

/**
 * Once a report reaches a terminal/visible state ({@code GENERATED},
 * {@code DOWNLOADED}, {@code EXPIRED}), the row is immutable. FAILED reports
 * can be re-requested as a brand-new {@code Report} record (never mutated).
 */
public final class ReportImmutabilityPolicy {

    private ReportImmutabilityPolicy() { }

    public static boolean isImmutable(ReportStatus status) {
        return status == ReportStatus.GENERATED
                || status == ReportStatus.DOWNLOADED
                || status == ReportStatus.EXPIRED;
    }

    /** A FAILED report can be retried by creating a NEW report — never reused. */
    public static boolean canRetryByCloning(ReportStatus status) {
        return status == ReportStatus.FAILED;
    }
}
