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
                // Batch-4: a worker failure may go straight to DEAD_LETTER when the
                // failing attempt is the last one (attempt_count >= MAX).
                EnumSet.of(ExportJobStatus.GENERATED, ExportJobStatus.FAILED,
                        ExportJobStatus.DEAD_LETTER, ExportJobStatus.CANCELLED));
        ALLOWED.put(ExportJobStatus.GENERATED,
                EnumSet.of(ExportJobStatus.DOWNLOADED, ExportJobStatus.EXPIRED));
        ALLOWED.put(ExportJobStatus.DOWNLOADED, EnumSet.of(ExportJobStatus.EXPIRED));
        // Batch-4: FAILED is a RETRYABLE resting state — the re-queuer may move it
        // back to QUEUED (re-dispatch) or, once attempts are exhausted, the worker
        // lands it in the terminal DEAD_LETTER.
        ALLOWED.put(ExportJobStatus.FAILED,
                EnumSet.of(ExportJobStatus.QUEUED, ExportJobStatus.DEAD_LETTER));
        ALLOWED.put(ExportJobStatus.CANCELLED, EnumSet.noneOf(ExportJobStatus.class));
        ALLOWED.put(ExportJobStatus.EXPIRED, EnumSet.noneOf(ExportJobStatus.class));
        // Terminal — never re-dispatched.
        ALLOWED.put(ExportJobStatus.DEAD_LETTER, EnumSet.noneOf(ExportJobStatus.class));
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
