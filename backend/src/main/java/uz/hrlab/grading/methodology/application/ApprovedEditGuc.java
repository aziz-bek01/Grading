package uz.hrlab.grading.methodology.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sets the transaction-local {@code app.methodology_approved_edit} GUC that the
 * DB triggers {@code prevent_locked_factor_changes} /
 * {@code prevent_locked_factor_level_changes} check before permitting a write
 * to an APPROVED methodology version's factors / levels (changelog 042).
 *
 * <p>Mirrors the calibration mechanism exactly:
 * {@code CalibrateEvaluationScoreUseCase} runs
 * {@code jdbcTemplate.execute("SET LOCAL app.calibration_in_progress = 'true'")}
 * inside its write transaction. {@code SET LOCAL} resets automatically at
 * commit/rollback so the flag can never leak across requests sharing a pooled
 * connection. This helper exists ONLY so the two factor write services do not
 * each inline the same SQL string (no new GUC-setting abstraction beyond the
 * single shared call); it MUST be invoked exclusively on the audited,
 * permission-checked APPROVED-edit branch, in the same transaction as the write.
 */
@Component
public class ApprovedEditGuc {

    private final JdbcTemplate jdbcTemplate;

    public ApprovedEditGuc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Enable the APPROVED-edit carve-out for the current transaction only. */
    public void enableForCurrentTransaction() {
        jdbcTemplate.execute("SET LOCAL app.methodology_approved_edit = 'on'");
    }
}
