package uz.hrlab.grading.integration.excel;

import java.util.List;

/**
 * Platform-authored description of the SECOND ("guide" / "Йўриқнома /
 * Инструкция") sheet that ships inside every downloadable import template.
 *
 * <p>The guide sheet is purely informational — it is never parsed on upload
 * (the importer targets the data sheet by name / index 0). Cell text is still
 * routed through {@link SafeCellWriter} by {@link ExcelWriter}, and rows
 * flagged {@link GuideRow#header()} get a bold + wrapped style.
 *
 * <p>This is a dumb data carrier: all wording lives in
 * {@code ImportTemplateGuide} (application layer), which derives the per-column
 * rules from the REAL parser / committer expectations so the instructions can
 * never drift from what the importer actually accepts.
 *
 * @param sheetName          POI sheet name (≤ 31 chars)
 * @param columnWidthsChars  per-column width in characters (clamped to 255)
 * @param rows               ordered guide rows (header + body)
 */
public record GuideSheet(
        String sheetName,
        int[] columnWidthsChars,
        List<GuideRow> rows) {

    /**
     * One row of the guide sheet.
     *
     * @param header {@code true} → bold + wrapped header styling
     * @param cells  ordered cell text (left to right)
     */
    public record GuideRow(boolean header, List<String> cells) {

        public static GuideRow header(String... cells) {
            return new GuideRow(true, List.of(cells));
        }

        public static GuideRow body(String... cells) {
            return new GuideRow(false, List.of(cells));
        }
    }
}
