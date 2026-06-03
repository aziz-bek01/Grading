package uz.hrlab.grading.integration.imports.domain;

/**
 * Canonical template codes (integration-blueprint §5.1). The registry on the
 * application layer maps these codes to expected columns, validators, and
 * required permissions.
 */
public final class ImportTemplateCode {

    private ImportTemplateCode() { }

    public static final String ORG_STRUCTURE_V1       = "ORG_STRUCTURE_V1";
    public static final String POSITION_CATALOG_V1    = "POSITION_CATALOG_V1";
    public static final String JOB_PROFILE_V1         = "JOB_PROFILE_V1";
    public static final String METHODOLOGY_FACTORS_V1 = "METHODOLOGY_FACTORS_V1";
    public static final String GRADE_BANDS_V1         = "GRADE_BANDS_V1";
}
