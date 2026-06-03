package uz.hrlab.grading.integration.excel;

import org.apache.poi.ss.usermodel.Cell;

/**
 * Formula-injection-safe cell writer (integration-blueprint §13.1).
 *
 * <p><b>This is the ONLY sanctioned path for writing user-originated string
 * values into an Excel cell anywhere in the platform.</b> Calling
 * {@code Cell.setCellValue(String)} directly from outside the
 * {@code uz.hrlab.grading.integration.excel} package is a build-time error —
 * see {@code ArchitectureTest#excelCellWritesMustGoThroughSafeCellWriter}
 * (added per MVP 2 Phase 2 integration review finding F2).
 *
 * <p>If a string cell value's first non-whitespace character is one of
 * {@code = + - @ \t \r \n} the writer prefixes the value with a single
 * apostrophe so spreadsheet engines treat it as literal text rather than a
 * formula.
 *
 * <p>Numbers, dates, and platform-authored formulas are written with their
 * raw types directly — sanitization applies only to user-originated strings.
 */
public final class SafeCellWriter {

    private SafeCellWriter() { }

    /** Returns the safe form of a user-supplied string for an Excel cell. */
    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) return value;
        int i = 0;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
            // technically only ' ' counts as safe whitespace; \t and \r/\n are dangerous.
            char c = value.charAt(i);
            if (c == '\t' || c == '\r' || c == '\n') break;
            i++;
        }
        if (i >= value.length()) return value;
        char first = value.charAt(i);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
    }

    /** Returns true if the given user string would be sanitized. */
    public static boolean wouldSanitize(String value) {
        return value != null && !value.equals(sanitize(value));
    }

    /**
     * Write a user-supplied string into the given cell, applying sanitization.
     * Callers MUST route every user-originated text cell through this method —
     * the {@code ExcelFormulaInjectionTest} guarantees the rule.
     */
    public static void writeString(Cell cell, String userValue) {
        cell.setCellValue(sanitize(userValue));
    }
}
