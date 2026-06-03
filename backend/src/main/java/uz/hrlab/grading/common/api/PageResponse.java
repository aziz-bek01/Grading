package uz.hrlab.grading.common.api;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Stable JSON envelope for paged results — decoupled from Spring's
 * {@code Page} JSON shape (which is internal and may change).
 *
 * <p>Contract-A: the array field is named {@code items} (frontend contract)
 * and the scalar fields serialize as {@code total_elements} / {@code total_pages}
 * via the global SNAKE_CASE Jackson strategy.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> PageResponse<T> of(Page<S> source, Function<S, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }

    /** Convenience factory for an already-mapped page (no element transform). */
    public static <T> PageResponse<T> from(Page<T> source) {
        return of(source, t -> t);
    }
}
