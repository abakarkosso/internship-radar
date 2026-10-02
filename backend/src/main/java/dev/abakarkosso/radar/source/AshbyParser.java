package dev.abakarkosso.radar.source;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;

/** Parses the public posting API: https://api.ashbyhq.com/posting-api/job-board/{board} */
public class AshbyParser {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Job> jobs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(String id, String title, String location, List<SecondaryLocation> secondaryLocations,
               String descriptionPlain, String descriptionHtml, String jobUrl, Boolean isListed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SecondaryLocation(String location) {
    }

    private final ObjectMapper mapper;

    public AshbyParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<RawPosting> parse(String json, String sourceKey, String company) {
        Response response = mapper.readValue(json, Response.class);
        if (response.jobs() == null) {
            // Must throw: an empty list would make ingestion close every posting on this board.
            throw new IllegalStateException(sourceKey + " response has no jobs list");
        }
        return response.jobs().stream()
                .filter(j -> !Boolean.FALSE.equals(j.isListed()))
                .map(j -> new RawPosting(
                        sourceKey,
                        j.id(),
                        company,
                        j.title(),
                        locations(j),
                        j.jobUrl(),
                        j.descriptionPlain() != null ? j.descriptionPlain() : Html.toText(j.descriptionHtml())))
                .toList();
    }

    /** A role listed in the US with Canada as a secondary location still belongs on the radar. */
    private static String locations(Job j) {
        List<String> all = new ArrayList<>();
        if (j.location() != null) {
            all.add(j.location());
        }
        if (j.secondaryLocations() != null) {
            j.secondaryLocations().stream().map(SecondaryLocation::location).forEach(all::add);
        }
        return String.join(" / ", all);
    }
}
