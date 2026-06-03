package uz.hrlab.grading.tenancy.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.hrlab.grading.common.persistence.AuditedJpaEntity;
import uz.hrlab.grading.tenancy.domain.ClientCompany;

import java.util.UUID;

/** Persistence representation of {@code public.client_companies} (database-blueprint §5.1). */
@Entity
@Table(name = "client_companies", schema = "public")
public class ClientCompanyJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "legal_name", nullable = false, length = 500)
    private String legalName;

    @Column(name = "brand_name", length = 255)
    private String brandName;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "country_code", length = 2, columnDefinition = "char(2)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String countryCode;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    protected ClientCompanyJpaEntity() { }

    public ClientCompanyJpaEntity(UUID id, UUID tenantId, String legalName, String brandName,
                                  String industry, String countryCode, String taxId) {
        this.id = id;
        this.tenantId = tenantId;
        this.legalName = legalName;
        this.brandName = brandName;
        this.industry = industry;
        this.countryCode = countryCode;
        this.taxId = taxId;
    }

    public ClientCompany toDomain() {
        return new ClientCompany(id, tenantId, legalName, brandName, industry, countryCode, taxId);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getLegalName() { return legalName; }
    public String getBrandName() { return brandName; }
    public String getIndustry() { return industry; }
    public String getCountryCode() { return countryCode; }
    public String getTaxId() { return taxId; }

    public void setLegalName(String legalName) { this.legalName = legalName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }
    public void setIndustry(String industry) { this.industry = industry; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public void setTaxId(String taxId) { this.taxId = taxId; }
}
