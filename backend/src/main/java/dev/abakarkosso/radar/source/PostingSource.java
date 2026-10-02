package dev.abakarkosso.radar.source;

import java.util.List;

/** One job board. Implementations should throw on failure so ingestion can skip closing that board's postings. */
public interface PostingSource {

    /** Stable key stored on every posting, e.g. "greenhouse:stripe". */
    String key();

    List<RawPosting> fetch() throws Exception;
}
