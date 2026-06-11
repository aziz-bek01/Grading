package uz.hrlab.grading.evaluation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.common.exception.BaseDomainException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.api.BulkCreateEvaluationsResponse;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * Bulk-create evaluations (Item 1, BE-1). Re-uses {@link CreateEvaluationUseCase}
 * per row so every existing security check (ABAC write-gate
 * {@code enforceCanWriteInDepartment}, duplicate guard, methodology-version
 * status check, tenant scoping) and the per-row {@code EVALUATION_CREATED} audit
 * remain authoritative — NO duplicated create logic, NO shortcut path.
 *
 * <p>Failures are <b>collected, not propagated</b>: one bad row must not roll
 * back successfully-created siblings. Per-row exceptions map to a stable
 * {@code errorCode} keyed on the row's {@code positionId} (no evaluation id
 * exists for a failed row):
 * <ul>
 *   <li>{@link TenantAccessDeniedException} (cross-tenant probe OR ABAC
 *       department-subtree denial) → {@code ACCESS_DENIED} — no existence
 *       reveal. The {@code ACCESS_DENIED_BY_ABAC} audit row is still written by
 *       {@code AbacGate} inside the inner use case.</li>
 *   <li>{@link PermissionDeniedException} → {@code ACCESS_DENIED}.</li>
 *   <li>duplicate-active / version-status {@link ValidationException} → the
 *       exception's own code ({@code VALIDATION_FAILED}) — surfaced as
 *       {@code ALREADY_EXISTS} when the message indicates an existing active
 *       evaluation.</li>
 *   <li>any other {@link BaseDomainException} → its code.</li>
 * </ul>
 *
 * <p>Each row runs in its own {@code REQUIRES_NEW} logical transaction so a
 * per-row rollback does not poison successful siblings.
 */
@Service
public class BulkCreateEvaluationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(BulkCreateEvaluationsUseCase.class);

    private final CreateEvaluationUseCase createUseCase;

    public BulkCreateEvaluationsUseCase(CreateEvaluationUseCase createUseCase) {
        this.createUseCase = createUseCase;
    }

    public BulkCreateEvaluationsResponse execute(List<CreateEvaluationCommand> rows) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_EDIT)) {
            throw new PermissionDeniedException();
        }
        if (rows == null || rows.isEmpty()) {
            throw new ValidationException("items must contain at least one row");
        }

        List<BulkCreateEvaluationsResponse.Failure> failed = new ArrayList<>();
        int created = 0;
        for (CreateEvaluationCommand cmd : rows) {
            try {
                createOne(cmd);
                created++;
            } catch (TenantAccessDeniedException ex) {
                // Cross-tenant probe OR ABAC out-of-subtree denial — same opaque code.
                failed.add(new BulkCreateEvaluationsResponse.Failure(
                        cmd.positionId(), "ACCESS_DENIED", "access denied for this position"));
            } catch (PermissionDeniedException ex) {
                failed.add(new BulkCreateEvaluationsResponse.Failure(
                        cmd.positionId(), "ACCESS_DENIED", "missing permission for this position"));
            } catch (ValidationException ex) {
                // Duplicate-active is the dominant validation case for create.
                String code = isDuplicate(ex) ? "ALREADY_EXISTS"
                        : (ex.getCode() == null ? "VALIDATION" : ex.getCode());
                failed.add(new BulkCreateEvaluationsResponse.Failure(
                        cmd.positionId(), code, ex.getMessage()));
            } catch (BaseDomainException ex) {
                failed.add(new BulkCreateEvaluationsResponse.Failure(
                        cmd.positionId(),
                        ex.getCode() == null ? "DOMAIN_REJECTED" : ex.getCode(),
                        ex.getMessage()));
            } catch (RuntimeException ex) {
                log.warn("Bulk create evaluation: unexpected failure position={}",
                        cmd.positionId(), ex);
                failed.add(new BulkCreateEvaluationsResponse.Failure(
                        cmd.positionId(), "INTERNAL_ERROR", "unexpected failure; see logs"));
            }
        }
        return new BulkCreateEvaluationsResponse(created, failed);
    }

    /**
     * Inner per-row transaction — REQUIRES_NEW so a per-row rollback (e.g. a
     * duplicate-key) does not poison sibling rows.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOne(CreateEvaluationCommand cmd) {
        createUseCase.create(cmd);
    }

    private static boolean isDuplicate(ValidationException ex) {
        String m = ex.getMessage();
        return m != null && m.toLowerCase().contains("already exists");
    }
}
