package uz.hrlab.grading.workflow.domain;

/**
 * Stage status derived by {@code WorkflowRecomputeService} from underlying
 * entity counts.
 *
 * <ul>
 *   <li>{@code NOT_STARTED} — no work yet</li>
 *   <li>{@code IN_PROGRESS} — partial completion</li>
 *   <li>{@code COMPLETE} — all required sub-entities approved / locked</li>
 *   <li>{@code BLOCKED} — waiting on something explicit (rare in MVP 2)</li>
 *   <li>{@code LOCKED_FUTURE} — stage is reserved for a later MVP
 *       (COMPENSATION / REPORTS)</li>
 * </ul>
 */
public enum WorkflowStageStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETE,
    BLOCKED,
    LOCKED_FUTURE
}
