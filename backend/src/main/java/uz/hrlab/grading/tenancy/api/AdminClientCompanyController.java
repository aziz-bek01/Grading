package uz.hrlab.grading.tenancy.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.hrlab.grading.common.api.PageResponse;
import uz.hrlab.grading.tenancy.application.GetClientCompanyDetailsQuery;
import uz.hrlab.grading.tenancy.application.ListClientCompaniesQuery;
import uz.hrlab.grading.tenancy.application.UpdateClientCompanyUseCase;

import java.util.UUID;

/**
 * Admin API for client_company rows (Sprint F, E9 — F-1 BE).
 *
 * <p>Kept separate from {@link AdminTenantController} because:
 * <ul>
 *   <li>permissions diverge: {@code CLIENT_*} codes vs {@code TENANT_*};</li>
 *   <li>tenant:client_company is 1:1 (UNIQUE constraint) but the management
 *       affordances differ — Client Company Admin can edit their brand
 *       profile without holding TENANT_EDIT;</li>
 *   <li>the URL surface ({@code /api/v1/admin/clients/{clientId}}) is keyed
 *       by client_company id, not by tenant id, so the controller has no
 *       {@code tenantId} path variable to validate.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/clients")
public class AdminClientCompanyController {

    private final ListClientCompaniesQuery listClients;
    private final GetClientCompanyDetailsQuery clientDetails;
    private final UpdateClientCompanyUseCase updateClient;

    public AdminClientCompanyController(ListClientCompaniesQuery listClients,
                                        GetClientCompanyDetailsQuery clientDetails,
                                        UpdateClientCompanyUseCase updateClient) {
        this.listClients = listClients;
        this.clientDetails = clientDetails;
        this.updateClient = updateClient;
    }

    // 1) GET /api/v1/admin/clients
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CLIENT_LIST','TENANT_READ')")
    public PageResponse<ClientCompanyListResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ClientCompanyListResponse> result = listClients.list(search, page, size);
        return PageResponse.of(result, r -> r);
    }

    // 2) GET /api/v1/admin/clients/{clientId}
    @GetMapping("/{clientId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_VIEW','CLIENT_LIST','TENANT_READ')")
    public ClientCompanyDetailResponse details(@PathVariable UUID clientId) {
        return clientDetails.byId(clientId);
    }

    // 3) PATCH /api/v1/admin/clients/{clientId}
    @PatchMapping("/{clientId}")
    @PreAuthorize("hasAnyAuthority('CLIENT_UPDATE','TENANT_EDIT')")
    public ClientCompanyDetailResponse patch(@PathVariable UUID clientId,
                                             @Valid @RequestBody UpdateClientCompanyRequest req) {
        return updateClient.patch(
                clientId,
                req.legalName(),
                req.brandName(),
                req.industry(),
                req.countryCode(),
                req.taxId());
    }
}
