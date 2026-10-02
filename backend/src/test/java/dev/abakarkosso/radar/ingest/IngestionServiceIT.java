package dev.abakarkosso.radar.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import dev.abakarkosso.radar.TestcontainersConfiguration;
import dev.abakarkosso.radar.eligibility.CoopRequirement;
import dev.abakarkosso.radar.posting.Posting;
import dev.abakarkosso.radar.posting.PostingRepository;
import dev.abakarkosso.radar.posting.RoleCategory;
import dev.abakarkosso.radar.source.PostingSource;
import dev.abakarkosso.radar.source.RawPosting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Runs ingestion against a real PostgreSQL in Docker, with a fake board in place of the network. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IngestionServiceIT {

    @Autowired
    IngestionService ingestion;

    @Autowired
    PostingRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void firstRunStoresOnlyCanadianStudentRolesWithEligibility() throws Exception {
        var board = new FakeBoard(
                raw("1", "Software Developer Co-op", "Toronto, ON",
                        "Must be enrolled in a recognized post-secondary co-op program. Java and SQL."),
                raw("2", "Senior Engineer", "Toronto, ON", "Not a student role."),
                raw("3", "Software Engineer Intern", "New York, NY", "Wrong country."));

        var result = ingestion.run(board);

        assertThat(result.fetched()).isEqualTo(3);
        assertThat(result.kept()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        Posting p = repository.findBySourceAndExternalId("fake", "1").orElseThrow();
        assertThat(p.getCategory()).isEqualTo(RoleCategory.SOFTWARE);
        assertThat(p.getCoopRequirement()).isEqualTo(CoopRequirement.REQUIRED);
        assertThat(p.getSkills()).containsExactly("Java", "SQL");
        assertThat(p.isOpen()).isTrue();
    }

    @Test
    void rerunKeepsFirstSeenAndClosesPostingsThatDisappear() throws Exception {
        var board = new FakeBoard(
                raw("1", "Data Analyst Intern", "Remote, Canada", "SQL"),
                raw("2", "Software Intern", "Ottawa, ON", "Python"));
        ingestion.run(board);
        var firstSeen = repository.findBySourceAndExternalId("fake", "1").orElseThrow().getFirstSeenAt();

        board.postings.removeIf(r -> r.externalId().equals("2"));
        var second = ingestion.run(board);

        assertThat(second.created()).isZero();
        assertThat(second.closed()).isEqualTo(1);
        assertThat(repository.findBySourceAndExternalId("fake", "1").orElseThrow().getFirstSeenAt())
                .isEqualTo(firstSeen);
        assertThat(repository.findBySourceAndExternalId("fake", "2").orElseThrow().isOpen()).isFalse();
    }

    @Test
    void failingBoardDoesNotCloseItsPostings() throws Exception {
        var board = new FakeBoard(raw("1", "Software Intern", "Toronto, ON", ""));
        ingestion.run(board);

        board.broken = true;
        ingestion.runAllFor(List.of(board));

        assertThat(repository.findBySourceAndExternalId("fake", "1").orElseThrow().isOpen()).isTrue();
    }

    private static RawPosting raw(String id, String title, String location, String description) {
        return new RawPosting("fake", id, "Acme", title, location, "https://acme.test/" + id, description);
    }

    static class FakeBoard implements PostingSource {
        final List<RawPosting> postings;
        boolean broken;

        FakeBoard(RawPosting... postings) {
            this.postings = new ArrayList<>(List.of(postings));
        }

        @Override
        public String key() {
            return "fake";
        }

        @Override
        public List<RawPosting> fetch() {
            if (broken) {
                throw new IllegalStateException("board down");
            }
            return List.copyOf(postings);
        }
    }
}
