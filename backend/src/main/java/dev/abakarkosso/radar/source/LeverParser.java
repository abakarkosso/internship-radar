package dev.abakarkosso.radar.source;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Parses the public postings API: https://api.lever.co/v0/postings/{board}?mode=json */
public class LeverParser {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(String id, String text, Categories categories, String descriptionPlain,
               List<Section> lists, String additionalPlain, String hostedUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Categories(String location, List<String> allLocations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Section(String text, String content) {
    }

    private final ObjectMapper mapper;

    public LeverParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<RawPosting> parse(String json, String sourceKey, String company) {
        List<Job> jobs = mapper.readValue(json, new TypeReference<List<Job>>() { });
        return jobs.stream()
                .map(j -> new RawPosting(
                        sourceKey,
                        j.id(),
                        company,
                        j.text(),
                        location(j.categories()),
                        j.hostedUrl(),
                        description(j)))
                .toList();
    }

    private static String location(Categories c) {
        if (c == null) {
            return "";
        }
        if (c.allLocations() != null && !c.allLocations().isEmpty()) {
            return String.join(" / ", c.allLocations());
        }
        return c.location() == null ? "" : c.location();
    }

    /** Lever keeps requirements in separate list sections, which is where the eligibility wording lives. */
    private static String description(Job j) {
        StringBuilder text = new StringBuilder(j.descriptionPlain() == null ? "" : j.descriptionPlain());
        if (j.lists() != null) {
            for (Section s : j.lists()) {
                text.append("\n\n").append(s.text()).append('\n').append(Html.toText(s.content()));
            }
        }
        if (j.additionalPlain() != null) {
            text.append("\n\n").append(j.additionalPlain());
        }
        return text.toString();
    }
}
