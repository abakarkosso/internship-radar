package dev.abakarkosso.radar.posting;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.abakarkosso.radar.TestcontainersConfiguration;
import dev.abakarkosso.radar.eligibility.CoopRequirement;
import dev.abakarkosso.radar.eligibility.Eligibility;
import dev.abakarkosso.radar.eligibility.GradWindow;
import dev.abakarkosso.radar.eligibility.WorkAuthorization;
import java.time.YearMonth;
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
    void exposesTheGraduationWindowForTheBrowserToCompare() throws Exception {
        save("7", "Class of 2028 Intern", "Fits", CoopRequirement.UNSPECIFIED, false,
                new GradWindow(YearMonth.of(2027, 12), YearMonth.of(2028, 8)), WorkAuthorization.UNSPECIFIED,
                Instant.parse("2026-09-01T00:00:00Z"));
        mvc.perform(get("/api/postings").param("q", "Class of 2028"))
                .andExpect(jsonPath("$.items[0].gradEarliest").value("2027-12"))
                .andExpect(jsonPath("$.items[0].gradLatest").value("2028-08"));
    }

    @Test
    void workStatusFilterHidesOnlyRolesThatExcludeTheStudent() throws Exception {
        Instant t = Instant.parse("2026-09-01T00:00:00Z");
        save("5", "Citizens Only", "Gov", CoopRequirement.UNSPECIFIED, false, null, WorkAuthorization.CITIZEN_OR_PR, t);
        save("6", "No Sponsorship", "Bank", CoopRequirement.UNSPECIFIED, false, null, WorkAuthorization.NO_SPONSORSHIP, t);

        mvc.perform(get("/api/postings").param("workStatus", "CITIZEN_OR_PR")).andExpect(jsonPath("$.totalItems").value(4));
        mvc.perform(get("/api/postings").param("workStatus", "WORK_PERMIT")).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get("/api/postings").param("workStatus", "NEEDS_SPONSORSHIP")).andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void postedWithinHoursKeepsOnlyRecentPostings() throws Exception {
        repository.deleteAll();
        Instant now = Instant.now();
        save("8", "Fresh Intern", "New", CoopRequirement.UNSPECIFIED, now.minus(Duration.ofHours(2)));
        save("9", "Older Intern", "Old", CoopRequirement.UNSPECIFIED, now.minus(Duration.ofHours(60)));

        mvc.perform(get("/api/postings").param("postedWithinHours", "48"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].company").value("New"));
        mvc.perform(get("/api/postings").param("postedWithinHours", "0")).andExpect(status().isBadRequest());
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
        save(id, title, company, coop, id.equals("1"), null, WorkAuthorization.UNSPECIFIED, seen);
    }

    private void save(String id, String title, String company, CoopRequirement coop, boolean graduateOnly,
                      GradWindow window, WorkAuthorization auth, Instant seen) {
        Posting p = new Posting("test", id, seen);
        p.refresh(company, title, "Toronto, ON", "https://example.com/" + id, "description",
                new Eligibility(coop, false, graduateOnly, window, auth, false, List.of(), List.of("SQL")), seen);
        repository.save(p);
    }
}
