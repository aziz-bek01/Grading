package uz.hrlab.grading.reporting.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed status transitions for {@link ReportStatus} — mirrors the
 * {@code ExportJob} 8-status FSM (integration-blueprint §9.1).
 *
 * <p>Terminal states: {@link ReportStatus#FAILED}, {@link ReportStatus#CANCELLED},
 * {@link ReportStatus#EXPIRED}.
 */
public final class ReportStatusTransitionPolicy {

    private static final Map<ReportStatus, Set<ReportStatus>> ALLOWED =
            new EnumMap<>(ReportStatus.class);

    static {
        ALLOWED.put(ReportStatus.REQUESTED,
                EnumSet.of(ReportStatus.QUEUED, ReportStatus.CANCELLED, ReportStatus.FAILED));
        ALLOWED.put(ReportStatus.QUEUED,
                EnumSet.of(ReportStatus.GENERATING, ReportStatus.CANCELLED, ReportStatus.FAILED));
        ALLOWED.put(ReportStatus.GENERATING,
                EnumSet.of(ReportStatus.GENERATED, ReportStatus.FAILED, ReportStatus.CANCELLED));
        ALLOWED.put(ReportStatus.GENERATED,
                EnumSet.of(ReportStatus.DOWNLOADED, ReportStatus.EXPIRED));
        ALLOWED.put(ReportStatus.DOWNLOADED, EnumSet.of(ReportStatus.EXPIRED));
        ALLOWED.put(ReportStatus.FAILED,    EnumSet.noneOf(ReportStatus.class));
        ALLOWED.put(ReportStatus.CANCELLED, EnumSet.noneOf(ReportStatus.class));
        ALLOWED.put(ReportStatus.EXPIRED,   EnumSet.noneOf(ReportStatus.class));
    }

    private ReportStatusTransitionPolicy() { }

    public static boolean isAllowed(ReportStatus from, ReportStatus to) {
        if (from == null || to == null) return false;
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(ReportStatus.class)).contains(to);
    }

    public static void assertAllowed(ReportStatus from, ReportStatus to) {
        if (!isAllowed(from, to)) {
            throw new ReportTransitionRejectedException(from, to);
        }
    }

    public static Set<ReportStatus> allowedTargets(ReportStatus from) {
        return EnumSet.copyOf(ALLOWED.getOrDefault(from, EnumSet.noneOf(ReportStatus.class)));
    }
}
