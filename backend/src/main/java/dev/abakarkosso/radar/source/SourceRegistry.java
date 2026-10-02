package dev.abakarkosso.radar.source;

import java.util.List;

/** Holds the configured boards. A wrapper rather than a bare List bean, which Spring would treat as a collection injection. */
public record SourceRegistry(List<PostingSource> sources) {
}
