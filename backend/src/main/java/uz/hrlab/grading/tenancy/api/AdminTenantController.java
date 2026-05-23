package uz.hrlab.grading.tenancy.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.tenancy.application.CreateTenantCommand;
import uz.hrlab.grading.tenancy.application.CreateTenantUseCase;
import uz.hrlab.grading.tenancy.domain.IsolationMode;
import uz.hrlab.grading.tenancy.domain.Tenant;

/**
 * Admin-only control-plane API for tenants.
 *
 * <p>Per architecture §13.1 and security-blueprint §7.4, this is the only
 * endpoint allowed to surface {@code tenant_id} in the URL — business APIs
 * never do (their tenant comes from JWT).
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
public class AdminTenantController {

    private final CreateTenantUseCase createTenantUseCase;

    public AdminTenantController(CreateTenantUseCase createTenantUseCase) {
        this.createTenantUseCase = createTenantUseCase;
    }

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
}
