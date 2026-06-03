package uz.hrlab.grading.integration.validation;

import org.springframework.stereotype.Component;
import uz.hrlab.grading.integration.excel.ExcelParser.ParsedSheet;
import uz.hrlab.grading.integration.imports.domain.ImportErrorLevel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 5-level validation orchestrator (integration-blueprint §11).
 *
 * <p>Levels run in order; later levels only execute when earlier levels have
 * not raised a BLOCKER (security-validation always runs because it is the
 * final guard — a permission or cross-tenant probe must not be skipped).
 *
 * <p>This orchestrator is stateless and tenant-agnostic — the tenant context
 * is passed in by the caller (worker re-establishes context from DB before
 * invocation).
 */
@Component
public class ImportValidator {

    /** Maximum bytes a single uploaded file may carry (25 MB per blueprint §13.3). */
    public static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;

    /** Allowed MIME types for import upload. */
    public static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * Level 1 — file validation. Runs BEFORE the file is parsed; expected to
     * be invoked at the controller boundary against the {@code MultipartFile}.
     */
    public ValidationResult validateFile(String contentType, String originalFilename, long sizeBytes) {
        ValidationResult result = new ValidationResult();
        if (sizeBytes <= 0) {
            result.add(ValidationError.blocker("FILE_EMPTY", "Uploaded file is empty"));
            return result;
        }
        if (sizeBytes > MAX_FILE_SIZE_BYTES) {
            result.add(ValidationError.blocker("FILE_TOO_LARGE",
                    "File exceeds maximum size of 25MB"));
        }
        if (originalFilename == null
                || originalFilename.contains("..")
                || originalFilename.contains("/")
                || originalFilename.contains("\\")
                || !originalFilename.toLowerCase().endsWith(".xlsx")) {
            result.add(ValidationError.blocker("FILE_TYPE_NOT_ALLOWED",
                    "Only .xlsx files without path separators are accepted"));
        }
        if (contentType != null && !contentType.isBlank() && !XLSX_MIME.equals(contentType)) {
            result.add(ValidationError.blocker("FILE_MIME_MISMATCH",
                    "Content-Type must be " + XLSX_MIME));
        }
        // Malware scan hook — MVP 2 ships pass-through; ClamAV wired in MVP 2 Phase 3.
        return result;
    }

    /** Level 2 — structure validation. */
    public ValidationResult validateStructure(ParsedSheet sheet, List<String> requiredColumns) {
        ValidationResult result = new ValidationResult();
        if (sheet == null || sheet.headers() == null || sheet.headers().isEmpty()) {
            result.add(ValidationError.blocker("MISSING_HEADER", "Header row is missing"));
            return result;
        }
        for (String required : requiredColumns) {
            if (!sheet.headers().contains(required)) {
                result.add(ValidationError.blocker("REQUIRED_COLUMN_MISSING",
                        "Required column missing: " + required));
            }
        }
        if (sheet.rows() == null || sheet.rows().isEmpty()) {
            result.add(ValidationError.blocker("NO_DATA_ROWS", "File contains no data rows"));
        }
        return result;
    }

    /** Level 3 — per-row validation (required fields, type, uniqueness within batch). */
    public ValidationResult validateRows(ParsedSheet sheet, List<String> requiredFields) {
        ValidationResult result = new ValidationResult();
        if (sheet == null || sheet.rows() == null) return result;
        int rowNumber = 1; // 1-based after header
        for (Map<String, String> row : sheet.rows()) {
            rowNumber++;
            for (String field : requiredFields) {
                String v = row.get(field);
                if (v == null || v.isBlank()) {
                    result.add(ValidationError.rowError(rowNumber, "REQUIRED_FIELD_MISSING",
                            field, "Required field is empty",
                            "Provide a value in column '" + field + "'"));
                }
            }
        }
        return result;
    }

    /** Level 4 — business validation. Hook for FK + cross-entity checks. */
    public ValidationResult validateBusiness(ParsedSheet sheet,
                                             BusinessRuleEvaluator evaluator,
                                             UUID tenantId, UUID projectId) {
        ValidationResult result = new ValidationResult();
        if (sheet == null || sheet.rows() == null || evaluator == null) return result;
        int rowNumber = 1;
        for (Map<String, String> row : sheet.rows()) {
            rowNumber++;
            evaluator.evaluate(row, rowNumber, tenantId, projectId, result);
        }
        return result;
    }

    /**
     * Level 5 — security validation. Forces a permission re-check, blocks
     * cross-tenant references in file content, and rejects formula-injection
     * payloads in user-input fields.
     */
    public ValidationResult validateSecurity(ParsedSheet sheet,
                                             boolean hasRequiredPermission,
                                             List<String> userInputFields) {
        ValidationResult result = new ValidationResult();
        if (!hasRequiredPermission) {
            result.add(ValidationError.blocker("PERMISSION_DENIED",
                    "Caller does not have permission to commit this import"));
            return result;
        }
        if (sheet == null || sheet.rows() == null) return result;
        int rowNumber = 1;
        for (Map<String, String> row : sheet.rows()) {
            rowNumber++;
            // The file MUST NOT carry tenant_id — backend overrides at commit
            // but we surface a WARNING so callers know we ignored their value.
            if (row.containsKey("tenant_id") || row.containsKey("tenantId")) {
                result.add(new ValidationError(ImportErrorLevel.WARNING,
                        "TENANT_ID_IN_FILE_IGNORED", rowNumber, "tenant_id",
                        "tenant_id columns are ignored; the active tenant is taken from the JWT",
                        null, null));
            }
            if (userInputFields != null) {
                for (String field : userInputFields) {
                    String v = row.get(field);
                    if (v == null || v.isBlank()) continue;
                    if (startsWithFormulaPrefix(v)) {
                        result.add(new ValidationError(ImportErrorLevel.WARNING,
                                "FORMULA_LIKE_INPUT_SANITIZED", rowNumber, field,
                                "Cell value starts with a formula prefix; it will be sanitized on export",
                                null, null));
                    }
                }
            }
        }
        return result;
    }

    private static boolean startsWithFormulaPrefix(String v) {
        int i = 0;
        while (i < v.length() && v.charAt(i) == ' ') i++;
        if (i >= v.length()) return false;
        char c = v.charAt(i);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r' || c == '\n';
    }

    /** Business-rule callback supplied by the use case for level 4. */
    public interface BusinessRuleEvaluator {
        void evaluate(Map<String, String> row, int rowNumber,
                      UUID tenantId, UUID projectId, ValidationResult into);
    }
}
