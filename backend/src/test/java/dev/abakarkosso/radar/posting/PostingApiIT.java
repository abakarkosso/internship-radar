package dev.abakarkosso.radar.posting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import dev.abakarkosso.radar.TestcontainersConfiguration;
import dev.abakarkosso.radar.eligibility.CoopRequirement;
import dev.abakarkosso.radar.eligibility.Eligibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PostingApiIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    PostingRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        save("1", "Software Developer Co-op", "Acme", CoopRequirement.REQUIRED, Instant.parse("2026-10-01T00:00:00Z"));
        save("2", "Data Analyst Intern", "Globex", CoopRequirement.NOT_REQUIRED, Instant.parse("2026-10-02T00:00:00Z"));
    }

    @Test
    void listsNewestFirst() throws Exception {
        mvc.perform(get("/api/postings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].company").value("Globex"))
                .andExpect(jsonPath("$.items[0].description").doesNotExist());
    }

    @Test
    void filtersOutCoopRequiredAndBySearch() throws Exception {
        mvc.perform(get("/api/postings").param("excludeCoopRequired", "true"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].company").value("Globex"));
        mvc.perform(get("/api/postings").param("q", "ACME"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].company").value("Acme"));
    }

    @Test
    void filtersOutGraduateOnlyRoles() throws Exception {
        mvc.perform(get("/api/postings").param("excludeGraduateOnly", "true"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].graduateDegreeRequired").value(false));
    }

    @Test
    void rejectsOversizedSearchWithProblemDetail() throws Exception {
        mvc.perform(get("/api/postings").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        mvc.perform(get("/api/postings").param("category", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void reportsStats() throws Exception {
        mvc.perform(get("/api/postings/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openPostings").value(2))
                .andExpect(jsonPath("$.companies").value(2))
                .andExpect(jsonPath("$.lastCheckedAt").exists());
    }

    @Test
    void sendsSecurityHeaders() throws Exception {
        mvc.perform(get("/api/postings"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    private void save(String id, String title, String company, CoopRequirement coop, Instant seen) {
        Posting p = new Posting("test", id, seen);
        p.refresh(company, title, "Toronto, ON", "https://example.com/" + id, "description",
                new Eligibility(coop, false, id.equals("1"), List.of(), List.of("SQL")), seen);
        repository.save(p);
    }
}
