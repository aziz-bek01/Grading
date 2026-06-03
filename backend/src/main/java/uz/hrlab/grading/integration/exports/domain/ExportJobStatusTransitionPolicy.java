package uz.hrlab.grading.integration.exports.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Allowed transitions for {@link ExportJobStatus} (integration-blueprint §9.1). */
public final class ExportJobStatusTransitionPolicy {

    private static final Map<ExportJobStatus, Set<ExportJobStatus>> ALLOWED =
            new EnumMap<>(ExportJobStatus.class);

    static {
        ALLOWED.put(ExportJobStatus.REQUESTED,
                EnumSet.of(ExportJobStatus.QUEUED, ExportJobStatus.CANCELLED, ExportJobStatus.FAILED));
        ALLOWED.put(ExportJobStatus.QUEUED,
                EnumSet.of(ExportJobStatus.GENERATING, ExportJobStatus.CANCELLED, ExportJobStatus.FAILED));
        ALLOWED.put(ExportJobStatus.GENERATING,
                EnumSet.of(ExportJobStatus.GENERATED, ExportJobStatus.FAILED, ExportJobStatus.CANCELLED));
        ALLOWED.put(ExportJobStatus.GENERATED,
                EnumSet.of(ExportJobStatus.DOWNLOADED, ExportJobStatus.EXPIRED));
        ALLOWED.put(ExportJobStatus.DOWNLOADED, EnumSet.of(ExportJobStatus.EXPIRED));
        ALLOWED.put(ExportJobStatus.FAILED, EnumSet.noneOf(ExportJobStatus.class));
        ALLOWED.put(ExportJobStatus.CANCELLED, EnumSet.noneOf(ExportJobStatus.class));
        ALLOWED.put(ExportJobStatus.EXPIRED, EnumSet.noneOf(ExportJobStatus.class));
    }

    private ExportJobStatusTransitionPolicy() { }

    public static boolean isAllowed(ExportJobStatus from, ExportJobStatus to) {
        if (from == null || to == null) return false;
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(ExportJobStatus.class)).contains(to);
    }

    public static void assertAllowed(ExportJobStatus from, ExportJobStatus to) {
        if (!isAllowed(from, to)) {
            throw new ExportJobTransitionRejectedException(from, to);
        }
    }

    public static Set<ExportJobStatus> allowedTargets(ExportJobStatus from) {
        return EnumSet.copyOf(ALLOWED.getOrDefault(from, EnumSet.noneOf(ExportJobStatus.class)));
    }
}
