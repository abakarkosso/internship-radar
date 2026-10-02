package dev.abakarkosso.radar.posting;

import java.util.List;

import org.springframework.data.domain.Page;

/** Stable JSON shape for a page, instead of serializing Spring's PageImpl internals. */
public record FeedPage(List<PostingSummary> items, int page, int size, long totalItems, int totalPages) {

    static FeedPage of(Page<PostingSummary> page) {
        return new FeedPage(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
