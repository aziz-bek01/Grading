package uz.hrlab.grading.evaluation.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sets the transaction-local {@code app.panel_reopen_in_progress} GUC that the
 * DB trigger {@code enforce_evaluation_lock_immutability} (changelog 043) checks
 * before permitting a prior contributing sheet to transition LOCKED -> COMPLETE
 * when an APPROVED panel is reopened to add an additional expert.
 *
 * <p>Mirrors the calibration / approved-edit mechanism EXACTLY:
 * {@code CalibrateEvaluationScoreUseCase} runs
 * {@code jdbcTemplate.execute("SET LOCAL app.calibration_in_progress = 'true'")}
 * and {@link uz.hrlab.grading.methodology.application.ApprovedEditGuc} runs
 * {@code SET LOCAL app.methodology_approved_edit = 'on'}, both inside the write
 * transaction. {@code SET LOCAL} resets automatically at commit/rollback so the
 * flag can NEVER leak across requests sharing a pooled connection. This helper
 * exists ONLY so the reopen use case does not inline the raw SQL string; it MUST
 * be invoked exclusively on the audited, permission+ABAC-checked reopen branch,
 * in the same transaction as the un-lock.
 */
@Component
public class PanelReopenGuc {

    private final JdbcTemplate jdbcTemplate;

    public PanelReopenGuc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Enable the panel-reopen un-lock carve-out for the current transaction only. */
    public void enableForCurrentTransaction() {
        jdbcTemplate.execute("SET LOCAL app.panel_reopen_in_progress = 'on'");
    }
}
