package uz.hrlab.grading.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EPIC-013 — proves the shared {@link Pagination} helper reproduces the exact
 * clamp behaviour that was previously copy-pasted across ~13 files, so migrating
 * every controller/query to it is a behaviour-preserving dedup. Guards the
 * "guess a bigger number" regression that truncated the panel list at size 20.
 */
class PaginationTest {

    @Test
    void maxPageSizeIsTwoHundred() {
        assertThat(Pagination.MAX_PAGE_SIZE).isEqualTo(200);
    }

    // ---- clamp(Pageable) : the four list controllers ------------------------

    @Test
    void clampCapsOversizedPageableAt200PreservingPageAndSort() {
        Sort sort = Sort.by("code").ascending();
        Pageable clamped = Pagination.clamp(PageRequest.of(3, 500, sort));

        assertThat(clamped.getPageSize()).isEqualTo(200);
        assertThat(clamped.getPageNumber()).isEqualTo(3);
        assertThat(clamped.getSort()).isEqualTo(sort);
    }

    @Test
    void clampLeavesInBoundsPageableUntouched() {
        Pageable original = PageRequest.of(1, 20, Sort.by("name"));
        assertThat(Pagination.clamp(original)).isSameAs(original);
    }

    @Test
    void clampBoundaryOf200IsNotAltered() {
        Pageable original = PageRequest.of(0, 200);
        assertThat(Pagination.clamp(original)).isSameAs(original);
    }

    @Test
    void clampNullFallsBackToPageZeroDefaultSize() {
        Pageable clamped = Pagination.clamp(null);
        assertThat(clamped.getPageNumber()).isEqualTo(0);
        assertThat(clamped.getPageSize()).isEqualTo(Pagination.DEFAULT_PAGE_SIZE);
    }

    // ---- clampSize(int) : the six query services ----------------------------

    @Test
    void clampSizeCapsAt200() {
        assertThat(Pagination.clampSize(500)).isEqualTo(200);
    }

    @Test
    void clampSizeFloorsAtOne() {
        assertThat(Pagination.clampSize(0)).isEqualTo(1);
        assertThat(Pagination.clampSize(-5)).isEqualTo(1);
    }

    @Test
    void clampSizeKeepsInRangeValue() {
        assertThat(Pagination.clampSize(37)).isEqualTo(37);
        assertThat(Pagination.clampSize(200)).isEqualTo(200);
    }

    // ---- of(page, size) : the export/import/report controllers --------------

    @Test
    void ofCapsSizeAt200AndFloorsPageAtZero() {
        Pageable p = Pagination.of(-1, 500);
        assertThat(p.getPageNumber()).isEqualTo(0);
        assertThat(p.getPageSize()).isEqualTo(200);
    }

    @Test
    void ofKeepsInRangeValues() {
        Pageable p = Pagination.of(2, 50);
        assertThat(p.getPageNumber()).isEqualTo(2);
        assertThat(p.getPageSize()).isEqualTo(50);
    }
}
