package uz.hrlab.grading.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Single source of truth for the page-size cap (EPIC-013).
 *
 * <p>Before this, {@code MAX_PAGE_SIZE = 200} was redeclared in ~13 files and
 * the clamp logic was copy-pasted across four controllers and six query
 * services — the "guess a bigger number" anti-pattern that caused the panel
 * {@code size=20 → 200} truncation bug. This helper centralises the constant
 * and the three clamp shapes that existed in the codebase, with BYTE-IDENTICAL
 * behaviour so no endpoint changes and the JSON pagination envelope
 * (page / size / total_pages / total_elements) is untouched.
 */
public final class Pagination {

    /** Maximum allowed page size per blueprint API-5 (F-409). */
    public static final int MAX_PAGE_SIZE = 200;

    /** Fallback size used when a resolved {@link Pageable} is {@code null}. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    private Pagination() {
    }

    /**
     * Cap a Spring-resolved {@link Pageable} so its size never exceeds
     * {@link #MAX_PAGE_SIZE}, preserving the page number and sort. A
     * {@code null} pageable falls back to page 0, size {@link #DEFAULT_PAGE_SIZE}
     * — matching the four controllers that inlined this exact method.
     */
    public static Pageable clamp(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    /**
     * Clamp a raw request size into {@code [1, MAX_PAGE_SIZE]} — matching the
     * {@code Math.min(Math.max(size, 1), MAX_PAGE_SIZE)} form the query
     * services used.
     */
    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /**
     * Build a {@link PageRequest} from raw page/size, flooring the page at 0 and
     * capping the size at {@link #MAX_PAGE_SIZE} — matching the
     * {@code PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, size))}
     * form the export/import/report controllers used (no lower bound on size,
     * preserved exactly).
     */
    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, size));
    }
}
