package dev.abakarkosso.radar.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AshbyParserTest {

    private final AshbyParser parser = new AshbyParser(JsonMapper.builder().build());

    @Test
    void parsesARealBoardResponseIncludingSecondaryLocations() throws Exception {
        List<RawPosting> postings = parser.parse(fixture("/fixtures/ashby-super.json"), "ashby:super.com", "Super");

        assertThat(postings).hasSize(2);
        RawPosting second = postings.get(1);
        assertThat(second.externalId()).isEqualTo("f194b0ae-3dfb-4356-9211-4f5e4b263ae4");
        assertThat(second.location()).isEqualTo("United States / Canada");
        assertThat(second.url()).startsWith("https://jobs.ashbyhq.com/");
        assertThat(second.description()).isNotBlank();
    }

    @Test
    void skipsUnlistedJobs() {
        String json = "{\"jobs\":[{\"id\":\"a\",\"title\":\"Hidden Intern\",\"isListed\":false}]}";
        assertThat(parser.parse(json, "ashby:x", "X")).isEmpty();
    }

    @Test
    void responseWithoutAJobsListIsAnErrorNotAnEmptyBoard() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> parser.parse("{\"success\":false}", "ashby:x", "X"))
                .isInstanceOf(IllegalStateException.class);
    }

    static String fixture(String path) throws Exception {
        try (var in = AshbyParserTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
