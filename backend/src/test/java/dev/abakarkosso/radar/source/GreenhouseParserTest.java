package dev.abakarkosso.radar.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GreenhouseParserTest {

    private final GreenhouseParser parser = new GreenhouseParser(JsonMapper.builder().build());

    @Test
    void parsesARealBoardResponse() throws Exception {
        String json;
        try (var in = getClass().getResourceAsStream("/fixtures/greenhouse-geotab.json")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<RawPosting> postings = parser.parse(json, "greenhouse:geotab", "Geotab");

        assertThat(postings).hasSize(3);
        RawPosting first = postings.getFirst();
        assertThat(first.source()).isEqualTo("greenhouse:geotab");
        assertThat(first.externalId()).isEqualTo("5382729008");
        assertThat(first.title()).isEqualTo("Global Operations Intern");
        assertThat(first.url()).startsWith("https://");
        // Entity-escaped HTML must come out as plain text.
        assertThat(first.description()).isNotBlank().doesNotContain("<p>").doesNotContain("&lt;");
    }

    @Test
    void responseWithoutAJobsListIsAnErrorNotAnEmptyBoard() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> parser.parse("{\"error\":\"rate limited\"}", "greenhouse:x", "X"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyBoardYieldsNoPostings() {
        assertThat(parser.parse("{\"jobs\": []}", "greenhouse:x", "X")).isEmpty();
    }
}
