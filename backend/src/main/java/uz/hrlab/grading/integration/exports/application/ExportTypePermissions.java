package uz.hrlab.grading.integration.exports.application;

import uz.hrlab.grading.access.application.PermissionCodes;
import uz.hrlab.grading.integration.exports.domain.ExportType;

/** Permission resolver for {@link ExportType} (integration-blueprint §6). */
public final class ExportTypePermissions {

    private ExportTypePermissions() { }

    public static String requiredPermission(ExportType type) {
        return switch (type) {
            case SALARY_RANGES, RED_GREEN_CIRCLE, COMPENSATION_SCENARIOS ->
                    PermissionCodes.SALARY_EXPORT;
            case REPORT_EXECUTIVE -> PermissionCodes.REPORT_EXPORT;
            case POSITION_CATALOG, JOB_PROFILES, METHODOLOGY,
                 EVALUATION_MATRIX, GRADE_STRUCTURE, GRADE_PYRAMID ->
                    PermissionCodes.EXPORT_REQUEST;
        };
    }

    public static boolean isSalaryBearing(ExportType type) {
        return switch (type) {
            case SALARY_RANGES, RED_GREEN_CIRCLE, COMPENSATION_SCENARIOS -> true;
            default -> false;
        };
    }
}
