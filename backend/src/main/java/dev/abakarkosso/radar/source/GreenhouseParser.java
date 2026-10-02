package dev.abakarkosso.radar.source;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

/** Parses the public boards API: https://boards-api.greenhouse.io/v1/boards/{board}/jobs?content=true */
public class GreenhouseParser {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Job> jobs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(long id, String title, Location location, String content,
               @JsonProperty("absolute_url") String absoluteUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Location(String name) {
    }

    private final ObjectMapper mapper;

    public GreenhouseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<RawPosting> parse(String json, String sourceKey, String company) {
        Response response = mapper.readValue(json, Response.class);
        if (response.jobs() == null) {
            // Must throw: an empty list would make ingestion close every posting on this board.
            throw new IllegalStateException(sourceKey + " response has no jobs list");
        }
        return response.jobs().stream()
                .map(j -> new RawPosting(
                        sourceKey,
                        String.valueOf(j.id()),
                        company,
                        j.title(),
                        j.location() == null ? "" : j.location().name(),
                        j.absoluteUrl(),
                        Html.toText(j.content())))
                .toList();
    }
}
