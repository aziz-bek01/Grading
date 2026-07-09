package uz.hrlab.grading.evaluation.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
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
import uz.hrlab.grading.evaluation.application.ApproveEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.ArchiveEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.BulkCreateEvaluationsUseCase;
import uz.hrlab.grading.evaluation.application.BulkSubmitEvaluationsUseCase;
import uz.hrlab.grading.evaluation.application.BulkUpsertEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.application.CalibrateEvaluationScoreCommand;
import uz.hrlab.grading.evaluation.application.CalibrateEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.application.CreateEvaluationCommand;
import uz.hrlab.grading.evaluation.application.CreateEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.DeleteEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.EvaluationQueries;
import uz.hrlab.grading.evaluation.application.LockEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.PreviewEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.application.PreviewScoreCommand;
import uz.hrlab.grading.evaluation.application.RequestEvaluationChangesUseCase;
import uz.hrlab.grading.evaluation.application.SubmitEvaluationUseCase;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreCommand;
import uz.hrlab.grading.evaluation.application.UpsertEvaluationScoreUseCase;
import uz.hrlab.grading.evaluation.domain.EvaluationStatus;
import uz.hrlab.grading.evaluation.infrastructure.EvaluationJpaEntity;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.common.api.Pagination;

import java.util.List;
import java.util.UUID;

/** Evaluation endpoints — every method @PreAuthorize'd; ABAC enforced server-side. */
@RestController
@RequestMapping("/api/v1/evaluations")
public class EvaluationController {

    private final CreateEvaluationUseCase createUseCase;
    private final BulkCreateEvaluationsUseCase bulkCreateUseCase;
    private final DeleteEvaluationUseCase deleteUseCase;
    private final UpsertEvaluationScoreUseCase upsertScoreUseCase;
    private final SubmitEvaluationUseCase submitUseCase;
    private final ApproveEvaluationUseCase approveUseCase;
    private final RequestEvaluationChangesUseCase requestChangesUseCase;
    private final LockEvaluationUseCase lockUseCase;
    private final ArchiveEvaluationUseCase archiveUseCase;
    private final CalibrateEvaluationScoreUseCase calibrateUseCase;
    private final PreviewEvaluationScoreUseCase previewUseCase;
    private final BulkUpsertEvaluationScoreUseCase bulkUpsertScoreUseCase;
    private final BulkSubmitEvaluationsUseCase bulkSubmitUseCase;
    private final EvaluationQueries queries;

    public EvaluationController(CreateEvaluationUseCase createUseCase,
                                BulkCreateEvaluationsUseCase bulkCreateUseCase,
                                DeleteEvaluationUseCase deleteUseCase,
                                UpsertEvaluationScoreUseCase upsertScoreUseCase,
                                SubmitEvaluationUseCase submitUseCase,
                                ApproveEvaluationUseCase approveUseCase,
                                RequestEvaluationChangesUseCase requestChangesUseCase,
                                LockEvaluationUseCase lockUseCase,
                                ArchiveEvaluationUseCase archiveUseCase,
                                CalibrateEvaluationScoreUseCase calibrateUseCase,
                                PreviewEvaluationScoreUseCase previewUseCase,
                                BulkUpsertEvaluationScoreUseCase bulkUpsertScoreUseCase,
                                BulkSubmitEvaluationsUseCase bulkSubmitUseCase,
                                EvaluationQueries queries) {
        this.createUseCase = createUseCase;
        this.bulkCreateUseCase = bulkCreateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.upsertScoreUseCase = upsertScoreUseCase;
        this.submitUseCase = submitUseCase;
        this.approveUseCase = approveUseCase;
        this.requestChangesUseCase = requestChangesUseCase;
        this.lockUseCase = lockUseCase;
        this.archiveUseCase = archiveUseCase;
        this.calibrateUseCase = calibrateUseCase;
        this.previewUseCase = previewUseCase;
        this.bulkUpsertScoreUseCase = bulkUpsertScoreUseCase;
        this.bulkSubmitUseCase = bulkSubmitUseCase;
        this.queries = queries;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public ResponseEntity<EvaluationResponse> create(@Valid @RequestBody CreateEvaluationRequest req) {
        var evaluation = createUseCase.create(new CreateEvaluationCommand(
                req.positionId(), req.methodologyVersionId(), req.evaluatorUserId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(EvaluationResponse.from(evaluation));
    }

    /**
     * Bulk-create one DRAFT evaluation per row. Each row flows through the
     * single-create use case so ABAC write-gate + duplicate guard +
     * EVALUATION_CREATED audit fire per row UNCHANGED. Per-row failures are
     * collected (keyed on position_id) — partial success returns 200.
     */
    @PostMapping("/bulk-create")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public BulkCreateEvaluationsResponse bulkCreate(
            @Valid @RequestBody BulkCreateEvaluationRequest req) {
        List<CreateEvaluationCommand> commands = req.items().stream()
                .map(i -> new CreateEvaluationCommand(
                        i.positionId(), i.methodologyVersionId(), i.evaluatorUserId()))
                .toList();
        return bulkCreateUseCase.execute(commands);
    }

    /**
     * Hard delete a pre-submission evaluation (Item 1, BE-2) — DRAFT, INCOMPLETE
     * or COMPLETE. Once SUBMITTED (SUBMITTED / APPROVED / LOCKED / ARCHIVED) the
     * evaluation keeps the ARCHIVE soft-delete path and is rejected with 400
     * EVALUATION_NOT_DELETABLE. Requires reason ≥ 5 chars; dependent score rows
     * are removed first; EVALUATION_DELETED is audited. Returns 204 No Content.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @Valid @RequestBody ReasonRequest req) {
        deleteUseCase.delete(id, req.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public ResponseEntity<PageResponse<?>> list(@RequestParam(required = false) UUID projectId,
                                                @RequestParam(required = false) UUID positionId,
                                                @RequestParam(required = false) UUID evaluatorUserId,
                                                @RequestParam(required = false) EvaluationStatus status,
                                                @RequestParam(required = false) String groupBy,
                                                @RequestParam(required = false) UUID factorId,
                                                @RequestParam(required = false) UUID departmentId,
                                                Pageable pageable) {
        Pageable safe = Pagination.clamp(pageable);
        // K-sheet UX branch — Excel-style per-factor grid across positions.
        if ("factor".equalsIgnoreCase(groupBy)) {
            Page<EvaluationByFactorRow> rows = queries.listByFactor(
                    projectId, factorId, status, departmentId, safe);
            return ResponseEntity.ok(PageResponse.from(rows));
        }
        Page<EvaluationJpaEntity> page = queries.list(projectId, positionId, evaluatorUserId,
                status, safe);
        return ResponseEntity.ok(PageResponse.of(page, e -> EvaluationResponse.from(e.toDomain())));
    }

    /**
     * Evaluator self "my evaluations" inbox — the sheets the caller themselves
     * must score (their own per-evaluator Evaluation rows, created at roster lock).
     * Self-scoped server-side ({@code tenant_id} + {@code evaluator_user_id} both
     * pinned from the security context — never a client param), so it deliberately
     * BYPASSES the department-scope fail-closed filter that the general {@code list}
     * read applies: an assigned committee member with no department-scope row must
     * still see their OWN sheets. Gated on {@code EVALUATION_READ} — the minimal
     * scoring permission a committee member holds — and safe because the data is
     * the caller's own by construction.
     */
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public List<MyEvaluationRow> listMine() {
        return queries.listMine();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public EvaluationResponse getById(@PathVariable UUID id) {
        return EvaluationResponse.from(queries.findById(id).toDomain());
    }

    @GetMapping("/{id}/scores")
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public List<EvaluationScoreResponse> listScores(@PathVariable UUID id) {
        return queries.findScoresByEvaluationId(id).stream()
                .map(s -> EvaluationScoreResponse.from(s.toDomain()))
                .toList();
    }

    @GetMapping("/{id}/calibration-history")
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public List<CalibrationEventResponse> listCalibrationHistory(@PathVariable UUID id) {
        return queries.findCalibrationHistory(id).stream()
                .map(c -> CalibrationEventResponse.from(c.toDomain()))
                .toList();
    }

    @PostMapping("/{id}/scores")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public EvaluationScoreResponse upsertScore(@PathVariable UUID id,
                                               @Valid @RequestBody UpsertScoreRequest req) {
        var score = upsertScoreUseCase.upsert(new UpsertEvaluationScoreCommand(
                id, req.factorId(), req.factorLevelId(), req.comment()));
        return EvaluationScoreResponse.from(score);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public EvaluationResponse submit(@PathVariable UUID id) {
        return EvaluationResponse.from(submitUseCase.submit(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('EVALUATION_APPROVE')")
    public EvaluationResponse approve(@PathVariable UUID id) {
        return EvaluationResponse.from(approveUseCase.approve(id));
    }

    @PostMapping("/{id}/request-changes")
    @PreAuthorize("hasAuthority('EVALUATION_APPROVE')")
    public EvaluationResponse requestChanges(@PathVariable UUID id,
                                             @Valid @RequestBody ReasonRequest req) {
        return EvaluationResponse.from(requestChangesUseCase.requestChanges(id, req.reason()));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('EVALUATION_LOCK')")
    public EvaluationResponse lock(@PathVariable UUID id) {
        return EvaluationResponse.from(lockUseCase.lock(id));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public EvaluationResponse archive(@PathVariable UUID id,
                                      @Valid @RequestBody ReasonRequest req) {
        return EvaluationResponse.from(archiveUseCase.archive(id, req.reason()));
    }

    @PostMapping("/{id}/calibrate")
    @PreAuthorize("hasAuthority('CALIBRATION_EDIT')")
    public CalibrationEventResponse calibrate(@PathVariable UUID id,
                                              @Valid @RequestBody CalibrateScoreRequest req) {
        var event = calibrateUseCase.calibrate(new CalibrateEvaluationScoreCommand(
                id, req.factorId(), req.newRawFactorScore(), req.reason()));
        return CalibrationEventResponse.from(event);
    }

    @PostMapping("/{id}/preview-score")
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public ScoringResultResponse previewExisting(@PathVariable UUID id,
                                                 @Valid @RequestBody PreviewScoreRequest req) {
        // path id reserved for future "preview against this evaluation's existing scores
        // but with overrides" — for now treat the same as a stateless preview.
        return previewScore(req);
    }

    @PostMapping("/preview-score")
    @PreAuthorize("hasAuthority('EVALUATION_READ')")
    public ScoringResultResponse previewScore(@Valid @RequestBody PreviewScoreRequest req) {
        List<PreviewScoreCommand.Selection> sel = req.selections() == null
                ? List.of()
                : req.selections().stream()
                        .map(s -> new PreviewScoreCommand.Selection(s.factorId(), s.factorLevelId()))
                        .toList();
        var result = previewUseCase.preview(new PreviewScoreCommand(
                req.methodologyVersionId(), sel));
        return ScoringResultResponse.from(result);
    }

    /**
     * Bulk PATCH score across many evaluations for a single factor (Excel
     * K-sheet UX). Failures collected per row — partial success returns 200.
     */
    @PostMapping("/factor/{factorId}/bulk-score")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public BulkOperationResponse bulkScoreSet(@PathVariable UUID factorId,
                                              @Valid @RequestBody BulkScoreRequest req) {
        return bulkUpsertScoreUseCase.execute(
                factorId, req.factorLevelId(), req.evaluationIds(), req.reason());
    }

    /**
     * Bulk transition COMPLETE→SUBMITTED. Completeness re-validated server-side
     * per evaluation; failures collected per row.
     */
    @PostMapping("/factor/{factorId}/bulk-submit")
    @PreAuthorize("hasAuthority('EVALUATION_EDIT')")
    public BulkOperationResponse bulkSubmit(@PathVariable UUID factorId,
                                            @Valid @RequestBody BulkSubmitRequest req) {
        // factorId in the path is contextual only (audit grouping by factor) —
        // the submit operation itself is evaluation-level, not factor-level.
        return bulkSubmitUseCase.execute(req.evaluationIds(), req.reason());
    }

}
