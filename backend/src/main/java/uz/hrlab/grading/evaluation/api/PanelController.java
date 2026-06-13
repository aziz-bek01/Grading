package uz.hrlab.grading.evaluation.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.evaluation.application.ArchivePanelUseCase;
import uz.hrlab.grading.evaluation.application.AssignEvaluatorUseCase;
import uz.hrlab.grading.evaluation.application.BulkCreatePanelsCommand;
import uz.hrlab.grading.evaluation.application.BulkCreatePanelsUseCase;
import uz.hrlab.grading.evaluation.application.CreatePanelCommand;
import uz.hrlab.grading.evaluation.application.CreatePanelUseCase;
import uz.hrlab.grading.evaluation.application.DeletePanelUseCase;
import uz.hrlab.grading.evaluation.application.LockRosterUseCase;
import uz.hrlab.grading.evaluation.application.PanelQueries;
import uz.hrlab.grading.evaluation.application.ReopenPanelUseCase;
import uz.hrlab.grading.evaluation.application.SubmitPanelToCeoUseCase;
import uz.hrlab.grading.evaluation.application.WithdrawEvaluatorUseCase;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.PanelAssignment;

import java.util.List;
import java.util.UUID;

/**
 * BE-16 — evaluation panel endpoints (multi-evaluator). snake_case wire +
 * PageResponse for lists. Approve / reject / request-changes are NOT here — a
 * panel is just {@code entity_type = EVALUATION_PANEL} and those decisions go
 * through the EXISTING ApprovalController.
 *
 * <p>Every method is @PreAuthorize'd; the use case re-checks the permission +
 * ABAC server-side (defense in depth).
 */
@RestController
@RequestMapping("/api/v1/panels")
public class PanelController {

    public static final int MAX_PAGE_SIZE = 200;

    private final CreatePanelUseCase createUseCase;
    private final BulkCreatePanelsUseCase bulkCreateUseCase;
    private final AssignEvaluatorUseCase assignUseCase;
    private final WithdrawEvaluatorUseCase withdrawUseCase;
    private final LockRosterUseCase lockRosterUseCase;
    private final SubmitPanelToCeoUseCase submitUseCase;
    private final DeletePanelUseCase deleteUseCase;
    private final ArchivePanelUseCase archiveUseCase;
    private final ReopenPanelUseCase reopenUseCase;
    private final PanelQueries queries;

    public PanelController(CreatePanelUseCase createUseCase,
                          BulkCreatePanelsUseCase bulkCreateUseCase,
                          AssignEvaluatorUseCase assignUseCase,
                          WithdrawEvaluatorUseCase withdrawUseCase,
                          LockRosterUseCase lockRosterUseCase,
                          SubmitPanelToCeoUseCase submitUseCase,
                          DeletePanelUseCase deleteUseCase,
                          ArchivePanelUseCase archiveUseCase,
                          ReopenPanelUseCase reopenUseCase,
                          PanelQueries queries) {
        this.createUseCase = createUseCase;
        this.bulkCreateUseCase = bulkCreateUseCase;
        this.assignUseCase = assignUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.lockRosterUseCase = lockRosterUseCase;
        this.submitUseCase = submitUseCase;
        this.deleteUseCase = deleteUseCase;
        this.archiveUseCase = archiveUseCase;
        this.reopenUseCase = reopenUseCase;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public ResponseEntity<PanelResponse> create(@Valid @RequestBody CreatePanelRequest req) {
        EvaluationPanel panel = createUseCase.create(new CreatePanelCommand(
                req.positionId(), req.methodologyVersionId(), req.minEvaluators()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PanelResponse.from(panel, null, 0, 0));
    }

    /**
     * BE-3 — bulk-create panels (department commission). One shared roster
     * applied to every position_id, all against one methodology_version_id. Each
     * row flows through the single-panel create + assign use cases UNCHANGED so
     * permission + tenant scoping + version-status guard + ABAC + per-panel /
     * per-seat audit fire per row. Per-row failures are collected (keyed on
     * position_id; ROSTER_PARTIAL carries seat_failures). Returns 201.
     */
    @PostMapping("/bulk-create")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public ResponseEntity<BulkCreatePanelsResponse> bulkCreate(
            @Valid @RequestBody BulkCreatePanelsRequest req) {
        List<BulkCreatePanelsCommand.RosterSeat> roster = (req.roster() == null ? List.<BulkCreatePanelsRequest.RosterSeat>of() : req.roster())
                .stream()
                .map(s -> new BulkCreatePanelsCommand.RosterSeat(
                        s.evaluatorUserId(), s.evaluatorRole()))
                .toList();
        BulkCreatePanelsResponse body = bulkCreateUseCase.execute(new BulkCreatePanelsCommand(
                req.methodologyVersionId(), req.positionIds(), roster));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * BE-5 — ADVISORY roster suggestions: department-director candidates for a
     * department (resolved from user_department_scopes ∩ the dept-scoped role).
     * Read-only; gated on {@code EVALUATION_PANEL_MANAGE} plus the ABAC
     * department read gate inside the query. The server re-validates membership
     * on the actual assign — this is a UI convenience only.
     */
    @GetMapping("/roster-suggestions")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public RosterSuggestionResponse rosterSuggestions(
            @RequestParam("project_id") UUID projectId,
            @RequestParam("department_id") UUID departmentId) {
        return queries.suggestDepartmentDirector(projectId, departmentId);
    }

    @PostMapping("/{id}/evaluators")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public ResponseEntity<PanelAssignmentResponse> assign(@PathVariable UUID id,
                                                          @Valid @RequestBody AssignEvaluatorRequest req) {
        PanelAssignment a = assignUseCase.assign(id, req.evaluatorUserId(), req.evaluatorRole());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PanelAssignmentResponse.from(a, null));
    }

    @DeleteMapping("/{id}/evaluators/{userId}")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public ResponseEntity<Void> withdraw(@PathVariable UUID id, @PathVariable UUID userId) {
        withdrawUseCase.withdraw(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/lock-roster")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public PanelResponse lockRoster(@PathVariable UUID id) {
        return PanelResponse.from(lockRosterUseCase.lockRoster(id), null, 0, 0);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public PanelResponse submit(@PathVariable UUID id) {
        return PanelResponse.from(submitUseCase.submit(id), null, 0, 0);
    }

    /**
     * Defect-2 BE — hard delete a {@code COLLECTING}-only panel (mirrors the
     * evaluation delete). Frees the active-panel uniqueness slot so a fresh panel
     * can be created for the unit's positions; roster seats cascade-removed.
     * A non-COLLECTING panel is rejected with 400 {@code PANEL_NOT_DELETABLE}
     * (use archive instead). Requires reason ≥ 5 chars; returns 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @Valid @RequestBody ReasonRequest req) {
        deleteUseCase.delete(id, req.reason());
        return ResponseEntity.noContent().build();
    }

    /**
     * Defect-2 BE — soft cancel (archive) a working panel
     * ({@code AWAITING_EVALUATIONS} / {@code AVERAGED} / {@code SUBMITTED} →
     * {@code ARCHIVED}; mirrors the evaluation archive). Preserves the audit trail
     * and frees the active-panel uniqueness slot. APPROVED / LOCKED / ARCHIVED are
     * rejected with 400 {@code PANEL_NOT_ARCHIVABLE}. Requires reason ≥ 5 chars.
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public PanelResponse archive(@PathVariable UUID id,
                                 @Valid @RequestBody ReasonRequest req) {
        return PanelResponse.from(archiveUseCase.archive(id, req.reason()), null, 0, 0);
    }

    /**
     * Defect-2 BE — manual reopen of a {@code SUBMITTED} / {@code AVERAGED} panel
     * back to {@code AWAITING_EVALUATIONS}. Reaches the SAME
     * {@link ReopenPanelUseCase} body invoked by the CEO CHANGES_REQUESTED
     * coupling (no duplicate reopen logic). Other statuses are a no-op.
     */
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('EVALUATION_PANEL_MANAGE')")
    public PanelResponse reopen(@PathVariable UUID id) {
        return PanelResponse.from(reopenUseCase.reopen(id), null, 0, 0);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public PageResponse<PanelResponse> list(@RequestParam(required = false) UUID projectId,
                                            @RequestParam(required = false) UUID positionId,
                                            Pageable pageable) {
        Page<PanelResponse> page = queries.list(projectId, positionId, clampPageSize(pageable));
        return PageResponse.from(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public PanelDetailResponse getById(@PathVariable UUID id) {
        return queries.getPanelDetail(id);
    }

    @GetMapping("/{id}/result")
    @PreAuthorize("hasAuthority('CAMPAIGN_RESULTS_VIEW')")
    public PanelResultResponse result(@PathVariable UUID id) {
        return queries.getResult(id);
    }

    private static Pageable clampPageSize(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20);
        }
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}
