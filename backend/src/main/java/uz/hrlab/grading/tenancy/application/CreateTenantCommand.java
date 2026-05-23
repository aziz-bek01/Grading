package uz.hrlab.grading.tenancy.application;

import uz.hrlab.grading.tenancy.domain.IsolationMode;

/** Validated command for {@link CreateTenantUseCase}. */
public record CreateTenantCommand(
        String slug,
        String displayName,
        String defaultLocale,
        IsolationMode isolationMode,
        String companyLegalName,
        String companyBrandName,
        String companyIndustry
) { }
