package uz.hrlab.grading.approval.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.hrlab.grading.approval.api.ApprovalRequestResponse;
import uz.hrlab.grading.approval.domain.ApprovalEntityType;
import uz.hrlab.grading.approval.domain.ApprovalRequest;
import uz.hrlab.grading.approval.domain.ApprovalRequestStatus;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestJpaEntity;
import uz.hrlab.grading.approval.infrastructure.ApprovalRequestRepository;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.common.exception.TenantAccessDeniedException;

import java.util.List;
import java.util.UUID;

/**
 * Read façade for the approval-request query endpoints (DETAIL, SEARCH).
 *
 * <h2>Why this bean exists — the RLS / transaction-boundary prod fix</h2>
 * The deployed runtime executes as the non-superuser {@code grading_runtime}
 * role, which is subject to {@code FORCE ROW LEVEL SECURITY} on
 * {@code approval_requests} / {@code approval_steps} / {@code approval_decisions}
 * and on the referenced entity tables. {@code RlsTenantSessionAspect} binds the
 * RLS GUC ({@code app.tenant_id}) with {@code SET LOCAL} ONLY inside an
 * aspect-advised {@code @Transactional} method — it does NOT advise direct
 * Spring-Data repository proxy calls.
 *
 * <p>{@code ApprovalController.getById} / {@code search} previously ran the
 * {@code findByIdAndTenantId} / {@code search} + {@code hydrate} + {@code enrich}
 * DIRECTLY in the controller, OUTSIDE any aspect-advised transaction. So the GUC
 * was never bound: RLS hid the {@code approval_requests} row → empty result →
 * {@link TenantAccessDeniedException} → 404 "Рухсат йўқ"; and the label-resolver
 * entity reads were likewise hidden → null labels → "Номсиз объект".
 *
 * <p>This service runs the WHOLE read path (the find AND the enrichment) inside a
 * single {@code @Transactional(readOnly = true)} method on a bean that is
 * DIFFERENT from the controller, so the aspect fires (no self-invocation /
 * proxy-bypass) and binds {@code app.tenant_id} once for every RLS-protected read
 * in that transaction. The controller is a thin delegate.
 *
 * <p>Tenant safety is unchanged: {@code tenantId} is server-derived and every
 * repository call is {@code *AndTenantId}-scoped (defense-in-depth on top of RLS);
 * a cross-tenant id still resolves to empty under the bound GUC → 404.
 */
@Service
public class ApprovalReadService {

    private final ApprovalRequestRepository requests;
    private final ApprovalQueries queries;
    private final ApprovalResponseAssembler assembler;

    public ApprovalReadService(ApprovalRequestRepository requests,
                               ApprovalQueries queries,
                               ApprovalResponseAssembler assembler) {
        this.requests = requests;
        this.queries = queries;
        this.assembler = assembler;
    }

    /**
     * DETAIL: load the tenant-scoped request, hydrate its steps and enrich it
     * (names + entity label) — all under ONE read transaction so the RLS GUC is
     * bound for the request read AND the label-resolver entity reads.
     *
     * @throws TenantAccessDeniedException if no in-tenant request matches (the
     *         safe 404 for a missing / cross-tenant id, indistinguishable to the
     *         caller — anti-BOLA).
     */
    @Transactional(readOnly = true)
    public ApprovalRequestResponse getById(UUID tenantId, UUID id) {
        ApprovalRequestJpaEntity req = requests.findByIdAndTenantId(id, tenantId)
                .orElseThrow(TenantAccessDeniedException::new);
        return assembler.enrich(tenantId, queries.hydrate(req));
    }

    /**
     * SEARCH (project list): page the tenant-scoped requests, hydrate + batch
     * enrich — under ONE read transaction (RLS GUC bound for the page read and
     * the per-row label reads).
     */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRequestResponse> search(UUID tenantId, UUID projectId,
                                                        ApprovalEntityType entityType,
                                                        ApprovalRequestStatus status,
                                                        Pageable pageable) {
        Page<ApprovalRequestJpaEntity> page = requests.search(tenantId, projectId,
                entityType, status, pageable);
        List<ApprovalRequestResponse> enriched = assembler.enrichAll(tenantId,
                page.getContent().stream().map(queries::hydrate).toList());
        return new PageResponse<>(enriched, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /**
     * SEARCH (entity-scoped): all requests for one (entityType, entityId),
     * hydrated + enriched, as a single page — under ONE read transaction.
     * The find is delegated to {@link FindApprovalRequestByEntityQuery} (itself
     * {@code @Transactional}); joining it here keeps the enrichment in the same
     * GUC-bound transaction.
     */
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRequestResponse> searchByEntity(
            UUID tenantId, List<ApprovalRequest> entityRequests) {
        List<ApprovalRequestResponse> rows = assembler.enrichAll(tenantId, entityRequests);
        return new PageResponse<>(rows, 0, Math.max(rows.size(), 1), rows.size(), 1);
    }

    /**
     * INBOX: enrich the caller's already-loaded pending requests (the list query
     * is itself {@code @Transactional}; this method re-binds the GUC for the
     * label-resolver reads so the inbox cards show resolved labels, not
     * "Номсиз объект").
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequestResponse> enrichInbox(UUID tenantId,
                                                     List<ApprovalRequest> pending) {
        return assembler.enrichAll(tenantId, pending);
    }
}
