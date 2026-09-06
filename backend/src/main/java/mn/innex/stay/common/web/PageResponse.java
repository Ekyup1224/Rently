package mn.innex.stay.common.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Paged payload shaped for AG Grid's server-side row model: a row array plus the
 * total count, so the grid can size its scrollbar without a second request.
 */
public record PageResponse<T>(List<T> rows, long total, int page, int size, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getTotalElements(),
                page.getNumber(), page.getSize(), page.getTotalPages());
    }

    public static <S, T> PageResponse<T> of(Page<S> page, java.util.function.Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages());
    }
}
