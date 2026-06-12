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
import uz.hrlab.grading.evaluation.application.AssignEvaluatorUseCase;
import uz.hrlab.grading.evaluation.application.CreatePanelCommand;
import uz.hrlab.grading.evaluation.application.CreatePanelUseCase;
import uz.hrlab.grading.evaluation.application.LockRosterUseCase;
import uz.hrlab.grading.evaluation.application.PanelQueries;
import uz.hrlab.grading.evaluation.application.SubmitPanelToCeoUseCase;
import uz.hrlab.grading.evaluation.application.WithdrawEvaluatorUseCase;
import uz.hrlab.grading.evaluation.domain.EvaluationPanel;
import uz.hrlab.grading.evaluation.domain.PanelAssignment;

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
    private final AssignEvaluatorUseCase assignUseCase;
    private final WithdrawEvaluatorUseCase withdrawUseCase;
    private final LockRosterUseCase lockRosterUseCase;
    private final SubmitPanelToCeoUseCase submitUseCase;
    private final PanelQueries queries;

    public PanelController(CreatePanelUseCase createUseCase,
                          AssignEvaluatorUseCase assignUseCase,
                          WithdrawEvaluatorUseCase withdrawUseCase,
                          LockRosterUseCase lockRosterUseCase,
                          SubmitPanelToCeoUseCase submitUseCase,
                          PanelQueries queries) {
        this.createUseCase = createUseCase;
        this.assignUseCase = assignUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.lockRosterUseCase = lockRosterUseCase;
        this.submitUseCase = submitUseCase;
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
