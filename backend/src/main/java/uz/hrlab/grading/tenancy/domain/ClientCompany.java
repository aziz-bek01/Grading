package uz.hrlab.grading.tenancy.domain;

import java.util.UUID;

/**
 * Client company linked to a tenant (database-blueprint §5.1).
 * One company per tenant in MVP 1.
 */
public final class ClientCompany {

    private final UUID id;
    private final UUID tenantId;
    private final String legalName;
    private final String brandName;
    private final String industry;
    private final String countryCode;
    private final String taxId;

    public ClientCompany(UUID id, UUID tenantId, String legalName, String brandName,
                         String industry, String countryCode, String taxId) {
        this.id = id;
        this.tenantId = tenantId;
        this.legalName = legalName;
        this.brandName = brandName;
        this.industry = industry;
        this.countryCode = countryCode;
        this.taxId = taxId;
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public String legalName() { return legalName; }
    public String brandName() { return brandName; }
    public String industry() { return industry; }
    public String countryCode() { return countryCode; }
    public String taxId() { return taxId; }
}
