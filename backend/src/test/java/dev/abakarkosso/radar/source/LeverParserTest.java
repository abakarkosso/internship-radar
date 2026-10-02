package dev.abakarkosso.radar.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LeverParserTest {

    private final LeverParser parser = new LeverParser(JsonMapper.builder().build());

    @Test
    void parsesARealBoardResponseAndKeepsRequirementSections() throws Exception {
        List<RawPosting> postings = parser.parse(AshbyParserTest.fixture("/fixtures/lever-wattpad.json"),
                "lever:wattpad", "Wattpad");

        assertThat(postings).hasSize(2);
        RawPosting first = postings.getFirst();
        assertThat(first.externalId()).isEqualTo("a322eb2d-dec8-4c83-829a-c71e0c9abdb7");
        assertThat(first.location()).isEqualTo("Toronto, Ontario");
        assertThat(first.url()).isEqualTo("https://jobs.lever.co/wattpad/a322eb2d-dec8-4c83-829a-c71e0c9abdb7");
        // Requirements live in Lever's "lists"; without them eligibility can't be read.
        assertThat(first.description()).contains("Required Qualifications:").doesNotContain("<li>");
    }
}
