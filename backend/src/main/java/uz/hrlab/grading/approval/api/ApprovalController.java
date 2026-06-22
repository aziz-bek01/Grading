package uz.hrlab.grading.approval.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.approval.application.ApprovalReadService;
import uz.hrlab.grading.approval.application.ApprovalResponseAssembler;
import uz.hrlab.grading.approval.application.ApproveStepUseCase;
import uz.hrlab.grading.approval.application.CancelApprovalRequestUseCase;
import uz.hrlab.grading.approval.application.CreateApprovalRequestCommand;
import uz.hrlab.grading.approval.application.CreateApprovalRequestUseCase;
import uz.hrlab.grading.approval.application.FindApprovalRequestByEntityQuery;
import uz.hrlab.grading.approval.application.ListMyPendingApprovalsQuery;
import uz.hrlab.grading.approval.application.RejectStepUseCase;
import uz.hrlab.grading.approval.application.RequestChangesUseCase;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.tenancy.application.TenantContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approval-requests")
public class ApprovalController {

    private final CreateApprovalRequestUseCase createUseCase;
    private final ApproveStepUseCase approveUseCase;
    private final RejectStepUseCase rejectUseCase;
    private final RequestChangesUseCase requestChangesUseCase;
    private final CancelApprovalRequestUseCase cancelUseCase;
    private final ListMyPendingApprovalsQuery inboxQuery;
    private final FindApprovalRequestByEntityQuery findByEntity;
    private final ApprovalReadService readService;
    private final ApprovalResponseAssembler assembler;

    public ApprovalController(CreateApprovalRequestUseCase createUseCase,
                              ApproveStepUseCase approveUseCase,
                              RejectStepUseCase rejectUseCase,
                              RequestChangesUseCase requestChangesUseCase,
                              CancelApprovalRequestUseCase cancelUseCase,
                              ListMyPendingApprovalsQuery inboxQuery,
                              FindApprovalRequestByEntityQuery findByEntity,
                              ApprovalReadService readService,
                              ApprovalResponseAssembler assembler) {
        this.createUseCase = createUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
        this.requestChangesUseCase = requestChangesUseCase;
        this.cancelUseCase = cancelUseCase;
        this.inboxQuery = inboxQuery;
        this.findByEntity = findByEntity;
        this.readService = readService;
        this.assembler = assembler;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('APPROVAL_REQUEST_CREATE')")
    public ResponseEntity<ApprovalRequestResponse> create(
            @Valid @RequestBody CreateApprovalRequestRequest req) {
        List<CreateApprovalRequestCommand.StepSpec> steps = req.steps().stream()
                .map(s -> new CreateApprovalRequestCommand.StepSpec(
                        s.stepOrder(), s.approverUserId(), s.requiredPermission()))
                .toList();
        var cmd = new CreateApprovalRequestCommand(
                req.projectId(), req.entityType(), req.entityId(),
                req.notesI18n(), steps);
        // BE-3 — enrich the write response so the FE shows the resolved localized
        // entity label (not a UUID) immediately after a decision/create. Reuses
        // the SAME assembler/label-resolver path as the read endpoints. The use
        // case runs FIRST so a tenant/permission denial surfaces from it (404),
        // not from the enrichment context lookup.
        var created = createUseCase.create(cmd);
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assembler.enrich(tenantId, created));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('APPROVAL_REQUEST_CREATE') or hasAuthority('APPROVAL_REQUEST_DECIDE')")
    public PageResponse<ApprovalRequestResponse> search(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) ApprovalEntityType entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) ApprovalRequestStatus status,
            Pageable pageable) {
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        if (entityType != null && entityId != null) {
            // Entity-scoped: return all (typically a small number) as a single page.
            // The find + batch enrich run inside ApprovalReadService's read
            // transaction so the RLS GUC is bound for the request AND label reads.
            return readService.searchByEntity(tenantId,
                    findByEntity.findAll(entityType, entityId));
        }
        // Page + hydrate + batch-enrich in ONE read transaction (RLS GUC bound).
        return readService.search(tenantId, projectId, entityType, status, pageable);
    }

    @GetMapping("/my-inbox")
    @PreAuthorize("hasAuthority('APPROVAL_REQUEST_DECIDE')")
    public List<ApprovalRequestResponse> inbox() {
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        // enrichInbox runs the label/name resolution under a read transaction so
        // the inbox cards resolve their labels (not "Номсиз объект").
        return readService.enrichInbox(tenantId, inboxQuery.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('APPROVAL_REQUEST_CREATE') or hasAuthority('APPROVAL_REQUEST_DECIDE')")
    public ApprovalRequestResponse getById(@PathVariable UUID id) {
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        // The find + hydrate + enrich all execute inside ApprovalReadService's
        // @Transactional(readOnly) method (a different bean → the RLS aspect
        // fires, no self-invocation), so app.tenant_id is bound and the row +
        // its entity label resolve under FORCE RLS (fixes 404 + "Номсиз объект").
        return readService.getById(tenantId, id);
    }

    @PostMapping("/{id}/steps/{stepId}/approve")
    @PreAuthorize("hasAuthority('APPROVAL_REQUEST_DECIDE')")
    public ApprovalRequestResponse approve(@PathVariable UUID id,
                                           @PathVariable UUID stepId,
                                           @RequestBody(required = false) ApprovalDecisionRequest req) {
        String notes = req == null ? null : req.notes();
        // Use case runs FIRST (tenant/permission denial → 404) then enrich.
        var decided = approveUseCase.approve(id, stepId, notes);
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        return assembler.enrich(tenantId, decided);
    }

    @PostMapping("/{id}/steps/{stepId}/reject")
    @PreAuthorize("hasAuthority('APPROVAL_REQUEST_DECIDE')")
    public ApprovalRequestResponse reject(@PathVariable UUID id,
                                          @PathVariable UUID stepId,
                                          @Valid @RequestBody ApprovalDecisionRequest req) {
        var decided = rejectUseCase.reject(id, stepId, req.reason());
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        return assembler.enrich(tenantId, decided);
    }

    @PostMapping("/{id}/steps/{stepId}/request-changes")
    @PreAuthorize("hasAuthority('APPROVAL_REQUEST_DECIDE')")
    public ApprovalRequestResponse requestChanges(@PathVariable UUID id,
                                                  @PathVariable UUID stepId,
                                                  @Valid @RequestBody ApprovalDecisionRequest req) {
        var decided = requestChangesUseCase.requestChanges(id, stepId, req.reason());
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        return assembler.enrich(tenantId, decided);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('APPROVAL_REQUEST_CANCEL')")
    public ApprovalRequestResponse cancel(@PathVariable UUID id) {
        var cancelled = cancelUseCase.cancel(id);
        UUID tenantId = TenantContextHolder.requireActive().tenantId();
        return assembler.enrich(tenantId, cancelled);
    }
}
