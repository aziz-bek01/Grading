package uz.hrlab.grading.tenancy.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.tenancy.application.ArchiveTenantUseCase;
import uz.hrlab.grading.tenancy.application.CreateTenantCommand;
import uz.hrlab.grading.tenancy.application.CreateTenantUseCase;
import uz.hrlab.grading.tenancy.application.GetTenantDetailsQuery;
import uz.hrlab.grading.tenancy.application.GetTenantStatsQuery;
import uz.hrlab.grading.tenancy.application.ListTenantsQuery;
import uz.hrlab.grading.tenancy.application.UpdateTenantUseCase;
import uz.hrlab.grading.tenancy.domain.IsolationMode;
import uz.hrlab.grading.tenancy.domain.Tenant;

import java.util.UUID;

/**
 * Admin-only control-plane API for tenants.
 *
 * <p>Per architecture §13.1 and security-blueprint §7.4, this is the only
 * endpoint family allowed to surface {@code tenant_id} in the URL — business
 * APIs never do (their tenant comes from JWT). ArchUnit Rule 8 explicitly
 * whitelists controllers under {@code ..admin..} package for the
 * {@code @PathVariable tenantId} pattern; the policy layer
 * ({@code TenantManagementPolicy}) still verifies the value against the
 * caller's membership set on every call.
 *
 * <p>Endpoint catalog (Sprint F, E9 — F-1 BE):
 * <ol>
 *   <li>POST   /                       — provision a new tenant + 1:1 client company</li>
 *   <li>GET    /                       — paged catalog (super-admin: all; others: own memberships)</li>
 *   <li>GET    /{tenantId}             — single tenant + embedded client company</li>
 *   <li>PATCH  /{tenantId}             — partial update (displayName, defaultLocale, status)</li>
 *   <li>POST   /{tenantId}/archive     — soft archive with mandatory reason</li>
 *   <li>GET    /{tenantId}/stats       — KPI counters (projects, users, last activity)</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class AdminTenantController {

    private final CreateTenantUseCase createTenantUseCase;
    private final ListTenantsQuery listTenants;
    private final GetTenantDetailsQuery tenantDetails;
    private final UpdateTenantUseCase updateTenant;
    private final ArchiveTenantUseCase archiveTenant;
    private final GetTenantStatsQuery tenantStats;

    public AdminTenantController(CreateTenantUseCase createTenantUseCase,
                                 ListTenantsQuery listTenants,
                                 GetTenantDetailsQuery tenantDetails,
                                 UpdateTenantUseCase updateTenant,
                                 ArchiveTenantUseCase archiveTenant,
                                 GetTenantStatsQuery tenantStats) {
        this.createTenantUseCase = createTenantUseCase;
        this.listTenants = listTenants;
        this.tenantDetails = tenantDetails;
        this.updateTenant = updateTenant;
        this.archiveTenant = archiveTenant;
        this.tenantStats = tenantStats;
    }

    // 1) POST /api/v1/admin/tenants
    @PostMapping
    @PreAuthorize("hasAuthority('TENANT_CREATE')")
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        CreateTenantCommand cmd = new CreateTenantCommand(
                request.slug(),
                request.displayName(),
                request.defaultLocale(),
                IsolationMode.valueOf(request.isolationMode()),
                request.companyLegalName(),
                request.companyBrandName(),
                request.companyIndustry()
        );
        Tenant created = createTenantUseCase.create(cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(created));
    }

    // 2) GET /api/v1/admin/tenants
    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public PageResponse<TenantListResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TenantListResponse> result = listTenants.list(search, status, page, size);
        return PageResponse.of(result, r -> r);
    }

    // 3) GET /api/v1/admin/tenants/{tenantId}
    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public TenantDetailResponse details(@PathVariable UUID tenantId) {
        return tenantDetails.byId(tenantId);
    }

    // 4) PATCH /api/v1/admin/tenants/{tenantId}
    @PatchMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('TENANT_EDIT')")
    public TenantDetailResponse patch(@PathVariable UUID tenantId,
                                      @Valid @RequestBody UpdateTenantRequest req) {
        return updateTenant.patch(tenantId, req.displayName(), req.defaultLocale(), req.status());
    }

    // 5) POST /api/v1/admin/tenants/{tenantId}/archive
    @PostMapping("/{tenantId}/archive")
    @PreAuthorize("hasAuthority('TENANT_EDIT')")
    public TenantDetailResponse archive(@PathVariable UUID tenantId,
                                        @Valid @RequestBody ArchiveTenantRequest req) {
        return archiveTenant.archive(tenantId, req.reason());
    }

    // 6) GET /api/v1/admin/tenants/{tenantId}/stats
    @GetMapping("/{tenantId}/stats")
    @PreAuthorize("hasAuthority('TENANT_READ')")
    public TenantStatsResponse stats(@PathVariable UUID tenantId) {
        return tenantStats.byId(tenantId);
    }
}
