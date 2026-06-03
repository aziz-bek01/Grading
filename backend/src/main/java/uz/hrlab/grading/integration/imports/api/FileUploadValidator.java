package uz.hrlab.grading.integration.imports.api;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uz.hrlab.grading.common.exception.ValidationException;

/**
 * Synchronous Level-1 file validator invoked at the HTTP boundary BEFORE any
 * persistence, audit-success, or storage IO. Rejects obviously-bad uploads
 * with the canonical {@link ValidationException} carrying a stable error code
 * (mapped to {@code 400 BAD_REQUEST} by {@code GlobalExceptionHandler}).
 *
 * <p>Per MVP 2 Phase 2 integration review finding F3: the upload use case
 * delegated all validation to the async pipeline; that meant a 100 MB or
 * non-XLSX upload still produced an {@code ImportBatch} row + storage write
 * before being rejected. Moving the cheap, synchronous checks up to the
 * controller boundary closes that gap.
 *
 * <p>The checks duplicate (defence-in-depth) those in
 * {@link uz.hrlab.grading.integration.validation.ImportValidator#validateFile}
 * — that validator still runs inside the use case so workers re-checking from
 * the persisted bytes remain protected.
 */
@Component
public class FileUploadValidator {

    /** 25 MB per integration-blueprint §13.3. */
    public static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;

    /** XLSX MIME type (Office Open XML). */
    public static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /**
     * Run the five synchronous boundary checks. Throws {@link ValidationException}
     * carrying a stable error code on the first failure. Callers should catch
     * upstream (via the global exception handler) to map to a 400 response and
     * to emit an {@code IMPORT_UPLOAD_REJECTED} audit event.
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("EMPTY_FILE",
                    "Uploaded file is missing or empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("FILE_TOO_LARGE",
                    "File exceeds maximum size of 25MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !XLSX_MIME.equalsIgnoreCase(contentType.trim())) {
            throw new ValidationException("INVALID_MIME_TYPE",
                    "Content-Type must be " + XLSX_MIME);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new ValidationException("INVALID_FILENAME",
                    "Original filename is required");
        }
        // Path-traversal guard runs BEFORE the extension check so a payload
        // like "../../etc/passwd.xlsx" is reported as INVALID_FILENAME (the
        // semantically accurate code) rather than passing the extension test.
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new ValidationException("INVALID_FILENAME",
                    "Filename must not contain path separators or '..'");
        }
        if (!filename.toLowerCase().endsWith(".xlsx")) {
            throw new ValidationException("INVALID_EXTENSION",
                    "Only .xlsx files are accepted");
        }
    }
}
