package uz.hrlab.grading.integration.excel;

/**
 * Import-side formula-injection neutraliser (Batch 5 — the symmetric half of
 * {@link SafeCellWriter}).
 *
 * <p>{@link SafeCellWriter} protects the EXPORT boundary: it prefixes a single
 * apostrophe onto any user-originated string whose first non-space character is
 * one of {@code = + - @ \t \r \n}. This class protects the IMPORT boundary: when
 * a user-uploaded Excel/CSV cell is read as <b>text</b>, the same dangerous
 * prefixes are neutralised <em>on the way in</em>, so a malicious cell cannot
 * round-trip back out into a later export (or be opened directly from a
 * downloaded copy) and execute as a spreadsheet formula.
 *
 * <p><b>Rule set is byte-for-byte mirrored from {@link SafeCellWriter}</b>:
 * dangerous-prefix set {@code = + - @ \t \r \n}, leading <i>space</i> runs are
 * skipped (a space-padded {@code "   =CMD()"} is still neutralised), and the
 * neutralisation is the identical apostrophe-prefix convention. Because the two
 * use exactly the same predicate and the same fix, the transformation is
 * <b>idempotent and stable</b>: sanitising a value that {@link SafeCellWriter}
 * already wrote is a no-op (an already-prefixed {@code "'=cmd"} starts with
 * {@code '}, which is not a dangerous prefix), so an import→export→import round
 * trip never double-prefixes.
 *
 * <p><b>Only text cells.</b> The caller ({@link ExcelParser#readCell}) invokes
 * this ONLY for genuine string/formula-string cells. A NUMERIC cell parsed as a
 * number (e.g. a negative number {@code -5} formatted to {@code "-5"}) and a
 * date cell are passed through untouched — they are not formula-injection
 * vectors because a spreadsheet re-imports them as numbers/dates, not as text
 * that begins with an operator. This is what preserves legitimate negative
 * numbers, phone numbers and leading-zero codes (those arrive as text but start
 * with a digit, {@code +} country-code handling aside — see below).
 *
 * <p><b>Pure / tenant-agnostic.</b> No tenant, project, IO, clock or Spring
 * dependency — a deterministic string→string function, unit-testable offline.
 */
public final class ImportCellSanitizer {

    private ImportCellSanitizer() { }

    /**
     * Returns the formula-injection-safe form of a user-supplied <b>text</b>
     * cell read from an uploaded Excel/CSV file.
     *
     * <p>Delegates the exact predicate + fix to {@link SafeCellWriter#sanitize}
     * so import and export agree on the dangerous-prefix set and the apostrophe
     * convention. Kept as a named import-side entry point (rather than calling
     * {@code SafeCellWriter.sanitize} inline at the parser) so the read boundary
     * is explicit, greppable and independently documented/tested.
     *
     * @param textCellValue the raw text extracted from a string/formula-string
     *                       cell (never a number/date — see class doc)
     * @return the same value, or, if it begins (after leading spaces) with a
     *         dangerous prefix, the value prefixed with a single apostrophe
     */
    public static String sanitizeText(String textCellValue) {
        return SafeCellWriter.sanitize(textCellValue);
    }

    /** True when {@link #sanitizeText} would change the given text value. */
    public static boolean wouldSanitize(String textCellValue) {
        return SafeCellWriter.wouldSanitize(textCellValue);
    }
}
