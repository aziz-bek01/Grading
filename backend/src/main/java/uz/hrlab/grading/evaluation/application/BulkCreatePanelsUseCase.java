package uz.hrlab.grading.evaluation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.audit.application.AuditAction;
import uz.hrlab.grading.audit.application.AuditEvent;
import uz.hrlab.grading.audit.application.AuditService;
import uz.hrlab.grading.common.exception.BaseDomainException;
import uz.hrlab.grading.common.exception.PermissionDeniedException;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;
import uz.hrlab.grading.common.exception.ValidationException;
import uz.hrlab.grading.evaluation.api.BulkCreatePanelsResponse;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.tenancy.application.TenantContext;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BE-1/BE-2 — bulk-create evaluation PANELS (department commission setup).
 * Mirrors {@link BulkCreateEvaluationsUseCase} EXACTLY: per-row
 * {@code REQUIRES_NEW} logical transactions, a failure collector keyed on
 * {@code positionId}, and NO rollback poison — one bad row never rolls back its
 * successfully-created siblings.
 *
 * <p>This use case ORCHESTRATES the existing single-panel building blocks; it
 * duplicates NO create/assign logic:
 * <ol>
 *   <li>Per {@code positionId} it calls {@link CreatePanelUseCase#create} UNCHANGED
 *       — keeping the {@code EVALUATION_PANEL_MANAGE} check, tenant scoping, the
 *       APPROVED|LOCKED version-status guard, the ABAC
 *       {@code enforceCanWriteInDepartment} gate, the
 *       {@code uq_panels_active_per_position_version} pre-check, and the per-panel
 *       {@code EVALUATION_PANEL_CREATED} audit.</li>
 *   <li>Then it loops the SHARED roster calling
 *       {@link AssignEvaluatorUseCase#assign} UNCHANGED per seat (its own
 *       permission + ABAC + COLLECTING-window checks and per-seat assign audit
 *       remain authoritative).</li>
 * </ol>
 *
 * <p>Failure mapping (same stable codes as bulk-create-evaluations):
 * <ul>
 *   <li>{@link TenantAccessDeniedException} (cross-tenant probe OR ABAC
 *       out-of-subtree denial) → {@code ACCESS_DENIED} (no existence reveal — the
 *       {@code ACCESS_DENIED_BY_ABAC} audit row is still written by the inner
 *       {@code AbacGate});</li>
 *   <li>{@link PermissionDeniedException} → {@code ACCESS_DENIED};</li>
 *   <li>duplicate-active {@link ValidationException} → {@code ALREADY_EXISTS},
 *       otherwise the exception's own code;</li>
 *   <li>any other {@link BaseDomainException} → its code;</li>
 *   <li>{@link RuntimeException} → {@code INTERNAL_ERROR}.</li>
 * </ul>
 *
 * <p>A PARTIAL roster failure (panel created, but a seat could not be assigned)
 * is reported at the position-row level as {@code ROSTER_PARTIAL} with a nested
 * per-seat reason list — the panel still exists in COLLECTING so the admin can
 * fix that one panel; the seat is never silently dropped.
 *
 * <p>The min-3 mandatory-trio rule is intentionally NOT enforced here — bulk
 * create only assigns the seats the admin provided and leaves panels in
 * COLLECTING; {@code LockRosterUseCase} remains the single enforcement point.
 */
@Service
public class BulkCreatePanelsUseCase {

    private static final Logger log = LoggerFactory.getLogger(BulkCreatePanelsUseCase.class);

    private final CreatePanelUseCase createPanelUseCase;
    private final AssignEvaluatorUseCase assignEvaluatorUseCase;
    private final AuditService audit;

    public BulkCreatePanelsUseCase(CreatePanelUseCase createPanelUseCase,
                                   AssignEvaluatorUseCase assignEvaluatorUseCase,
                                   AuditService audit) {
        this.createPanelUseCase = createPanelUseCase;
        this.assignEvaluatorUseCase = assignEvaluatorUseCase;
        this.audit = audit;
    }

    public BulkCreatePanelsResponse execute(BulkCreatePanelsCommand command) {
        TenantContext ctx = TenantContextHolder.requireActive();
        if (!ctx.hasPermission(PermissionCodes.EVALUATION_PANEL_MANAGE)) {
            throw new PermissionDeniedException();
        }
        if (command == null || command.methodologyVersionId() == null) {
            throw new ValidationException("methodology_version_id is required");
        }
        if (command.positionIds() == null || command.positionIds().isEmpty()) {
            throw new ValidationException("position_ids must contain at least one position");
        }
        List<BulkCreatePanelsCommand.RosterSeat> roster =
                command.roster() == null ? List.of() : command.roster();

        List<BulkCreatePanelsResponse.Failure> failed = new ArrayList<>();
        int created = 0;
        for (UUID positionId : command.positionIds()) {
            EvaluationPanel panel;
            try {
                panel = createPanelInNewTx(positionId, command.methodologyVersionId());
            } catch (TenantAccessDeniedException ex) {
                // Cross-tenant probe OR ABAC out-of-subtree denial — opaque code.
                failed.add(BulkCreatePanelsResponse.Failure.of(
                        positionId, "ACCESS_DENIED", "access denied for this position"));
                continue;
            } catch (PermissionDeniedException ex) {
                failed.add(BulkCreatePanelsResponse.Failure.of(
                        positionId, "ACCESS_DENIED", "missing permission for this position"));
                continue;
            } catch (ValidationException ex) {
                String code = isDuplicate(ex) ? "ALREADY_EXISTS"
                        : (ex.getCode() == null ? "VALIDATION" : ex.getCode());
                failed.add(BulkCreatePanelsResponse.Failure.of(
                        positionId, code, ex.getMessage()));
                continue;
            } catch (BaseDomainException ex) {
                failed.add(BulkCreatePanelsResponse.Failure.of(
                        positionId,
                        ex.getCode() == null ? "DOMAIN_REJECTED" : ex.getCode(),
                        ex.getMessage()));
                continue;
            } catch (RuntimeException ex) {
                log.warn("Bulk create panel: unexpected create failure position={}", positionId, ex);
                failed.add(BulkCreatePanelsResponse.Failure.of(
                        positionId, "INTERNAL_ERROR", "unexpected failure; see logs"));
                continue;
            }

            // Panel exists (COLLECTING). The row counts as created; any failed seat
            // is reported as ROSTER_PARTIAL — never silently dropped, never
            // un-creates the panel.
            created++;
            List<BulkCreatePanelsResponse.SeatFailure> seatFailures =
                    assignRoster(panel.id(), roster);
            if (!seatFailures.isEmpty()) {
                failed.add(BulkCreatePanelsResponse.Failure.rosterPartial(positionId, seatFailures));
            }
        }

        // BE-6 — ONE wrapper audit event so a department commission setup is
        // traceable as a single operation, in addition to every inherited
        // per-panel EVALUATION_PANEL_CREATED and per-seat assign audit.
        audit.record(AuditEvent.builder()
                .tenantId(ctx.tenantId())
                .actorUserId(ctx.userId())
                .action(AuditAction.EVALUATION_PANEL_BULK_CREATED)
                .entityType("EvaluationPanel")
                .reason("methodology_version_id=" + command.methodologyVersionId()
                        + ";requested_count=" + command.positionIds().size()
                        + ";created_count=" + created
                        + ";failed_count=" + failed.size())
                .build());

        return new BulkCreatePanelsResponse(created, failed);
    }

    /**
     * Inner per-position panel create — REQUIRES_NEW so a per-row rollback (e.g.
     * the one-active-panel guard, an ABAC deny) does not poison sibling rows.
     * Delegates to the UNCHANGED {@link CreatePanelUseCase#create}; minEvaluators
     * is left null so the product floor of 3 applies (panels open COLLECTING).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EvaluationPanel createPanelInNewTx(UUID positionId, UUID methodologyVersionId) {
        return createPanelUseCase.create(
                new CreatePanelCommand(positionId, methodologyVersionId, null));
    }

    /**
     * Assign every shared roster seat to the created panel. Each seat runs in its
     * OWN REQUIRES_NEW transaction so a single un-assignable evaluator never rolls
     * back the panel nor its already-assigned siblings. Per-seat exceptions are
     * collected into the returned list (BE-2 ROSTER_PARTIAL), never propagated.
     */
    private List<BulkCreatePanelsResponse.SeatFailure> assignRoster(
            UUID panelId, List<BulkCreatePanelsCommand.RosterSeat> roster) {
        List<BulkCreatePanelsResponse.SeatFailure> seatFailures = new ArrayList<>();
        for (BulkCreatePanelsCommand.RosterSeat seat : roster) {
            try {
                assignSeatInNewTx(panelId, seat);
            } catch (RuntimeException ex) {
                seatFailures.add(new BulkCreatePanelsResponse.SeatFailure(
                        seat.evaluatorRole() == null ? null : seat.evaluatorRole().name(),
                        seat.evaluatorUserId(),
                        seatReason(ex)));
            }
        }
        return seatFailures;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assignSeatInNewTx(UUID panelId, BulkCreatePanelsCommand.RosterSeat seat) {
        assignEvaluatorUseCase.assign(panelId, seat.evaluatorUserId(), seat.evaluatorRole());
    }

    private static String seatReason(RuntimeException ex) {
        if (ex instanceof TenantAccessDeniedException) {
            return "ACCESS_DENIED: evaluator not assignable on this panel";
        }
        if (ex instanceof PermissionDeniedException) {
            return "ACCESS_DENIED: missing permission to assign this evaluator";
        }
        if (ex instanceof BaseDomainException de) {
            return (de.getCode() == null ? "VALIDATION" : de.getCode())
                    + ": " + de.getMessage();
        }
        log.warn("Bulk create panel: unexpected seat-assign failure panelMember={}",
                ex.getMessage(), ex);
        return "INTERNAL_ERROR: unexpected failure; see logs";
    }

    private static boolean isDuplicate(ValidationException ex) {
        String m = ex.getMessage();
        return m != null && m.toLowerCase().contains("already exists");
    }
}
