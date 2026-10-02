package dev.abakarkosso.radar.source;

/** A posting as a source returns it, before filtering and enrichment. */
public record RawPosting(
        String source,
        String externalId,
        String company,
        String title,
        String location,
        String url,
        String description) {
}
