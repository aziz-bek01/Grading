package uz.hrlab.grading.integration.imports.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Allowed transitions for {@link ImportBatchStatus} (integration-blueprint §8.1).
 *
 * <p>Terminal states (SCAN_FAILED, COMMITTED, PARTIALLY_COMMITTED, FAILED,
 * CANCELLED, ARCHIVED) have no outgoing transitions except ARCHIVED via
 * retention policy. Every illegal attempted transition is rejected with a
 * domain exception — never silently coerced.
 */
public final class ImportBatchStatusTransitionPolicy {

    private static final Map<ImportBatchStatus, Set<ImportBatchStatus>> ALLOWED =
            new EnumMap<>(ImportBatchStatus.class);

    static {
        ALLOWED.put(ImportBatchStatus.UPLOADED,
                EnumSet.of(ImportBatchStatus.SCANNING, ImportBatchStatus.FAILED, ImportBatchStatus.CANCELLED));
        ALLOWED.put(ImportBatchStatus.SCANNING,
                EnumSet.of(ImportBatchStatus.PARSING, ImportBatchStatus.SCAN_FAILED, ImportBatchStatus.CANCELLED));
        ALLOWED.put(ImportBatchStatus.PARSING,
                EnumSet.of(ImportBatchStatus.VALIDATING, ImportBatchStatus.FAILED, ImportBatchStatus.CANCELLED));
        ALLOWED.put(ImportBatchStatus.VALIDATING,
                EnumSet.of(ImportBatchStatus.READY_FOR_REVIEW,
                        ImportBatchStatus.VALIDATION_FAILED,
                        ImportBatchStatus.FAILED,
                        ImportBatchStatus.CANCELLED));
        ALLOWED.put(ImportBatchStatus.READY_FOR_REVIEW,
                EnumSet.of(ImportBatchStatus.READY_TO_COMMIT,
                        ImportBatchStatus.VALIDATING,            // re-validate after mapping
                        ImportBatchStatus.CANCELLED));
        ALLOWED.put(ImportBatchStatus.READY_TO_COMMIT,
                EnumSet.of(ImportBatchStatus.COMMITTING, ImportBatchStatus.CANCELLED));
        ALLOWED.put(ImportBatchStatus.COMMITTING,
                EnumSet.of(ImportBatchStatus.COMMITTED,
                        ImportBatchStatus.PARTIALLY_COMMITTED,
                        ImportBatchStatus.FAILED));
        ALLOWED.put(ImportBatchStatus.COMMITTED, EnumSet.of(ImportBatchStatus.ARCHIVED));
        ALLOWED.put(ImportBatchStatus.PARTIALLY_COMMITTED, EnumSet.of(ImportBatchStatus.ARCHIVED));
        ALLOWED.put(ImportBatchStatus.FAILED, EnumSet.of(ImportBatchStatus.ARCHIVED));
        ALLOWED.put(ImportBatchStatus.CANCELLED, EnumSet.of(ImportBatchStatus.ARCHIVED));
        ALLOWED.put(ImportBatchStatus.SCAN_FAILED, EnumSet.of(ImportBatchStatus.ARCHIVED));
        ALLOWED.put(ImportBatchStatus.VALIDATION_FAILED,
                EnumSet.of(ImportBatchStatus.VALIDATING,        // user fixed mapping
                        ImportBatchStatus.CANCELLED,
                        ImportBatchStatus.ARCHIVED));
        ALLOWED.put(ImportBatchStatus.ARCHIVED, EnumSet.noneOf(ImportBatchStatus.class));
    }

    private ImportBatchStatusTransitionPolicy() { }

    public static boolean isAllowed(ImportBatchStatus from, ImportBatchStatus to) {
        if (from == null || to == null) return false;
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(ImportBatchStatus.class)).contains(to);
    }

    public static void assertAllowed(ImportBatchStatus from, ImportBatchStatus to) {
        if (!isAllowed(from, to)) {
            throw new ImportBatchTransitionRejectedException(from, to);
        }
    }

    public static Set<ImportBatchStatus> allowedTargets(ImportBatchStatus from) {
        return EnumSet.copyOf(ALLOWED.getOrDefault(from, EnumSet.noneOf(ImportBatchStatus.class)));
    }
}
