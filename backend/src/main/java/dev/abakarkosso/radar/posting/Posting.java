package dev.abakarkosso.radar.posting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import dev.abakarkosso.radar.eligibility.CoopRequirement;
import dev.abakarkosso.radar.eligibility.Eligibility;
import dev.abakarkosso.radar.eligibility.GradWindow;
import dev.abakarkosso.radar.eligibility.WorkAuthorization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String source;
    private String externalId;
    private String company;
    private String title;
    private String location;
    private String url;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    private RoleCategory category;

    @Enumerated(EnumType.STRING)
    private CoopRequirement coopRequirement;

    private boolean mustReturnToSchool;
    private boolean graduateDegreeRequired;
    private LocalDate gradEarliest;
    private LocalDate gradLatest;

    @Enumerated(EnumType.STRING)
    private WorkAuthorization workAuthorization;
    private Integer[] termMonths;
    private String[] skills;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private boolean open;

    private static final int MAX_DESCRIPTION = 50_000;

    protected Posting() {
    }

    public Posting(String source, String externalId, Instant now) {
        this.source = source;
        this.externalId = externalId;
        this.firstSeenAt = now;
    }

    /** Refresh from the latest fetch. first_seen_at never moves; it is what "new" means. */
    public void refresh(String company, String title, String location, String url, String description,
                        Eligibility eligibility, Instant now) {
        this.company = truncate(company, 200);
        this.title = truncate(title, 500);
        this.location = truncate(location, 500);
        this.url = url;
        this.description = truncate(description, MAX_DESCRIPTION);
        this.category = RoleCategory.fromTitle(title);
        this.coopRequirement = eligibility.coop();
        this.mustReturnToSchool = eligibility.mustReturnToSchool();
        this.graduateDegreeRequired = eligibility.graduateDegreeRequired();
        GradWindow window = eligibility.gradWindow();
        this.gradEarliest = window == null || window.earliest() == null ? null : window.earliest().atDay(1);
        this.gradLatest = window == null || window.latest() == null ? null : window.latest().atDay(1);
        this.workAuthorization = eligibility.workAuthorization();
        this.termMonths = eligibility.termMonths().toArray(Integer[]::new);
        this.skills = eligibility.skills().toArray(String[]::new);
        this.lastSeenAt = now;
        this.open = true;
    }

    public void close() {
        this.open = false;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public String getCompany() { return company; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getUrl() { return url; }
    public String getDescription() { return description; }
    public RoleCategory getCategory() { return category; }
    public CoopRequirement getCoopRequirement() { return coopRequirement; }
    public boolean isMustReturnToSchool() { return mustReturnToSchool; }
    public boolean isGraduateDegreeRequired() { return graduateDegreeRequired; }
    public YearMonth getGradEarliest() { return gradEarliest == null ? null : YearMonth.from(gradEarliest); }
    public YearMonth getGradLatest() { return gradLatest == null ? null : YearMonth.from(gradLatest); }
    public WorkAuthorization getWorkAuthorization() { return workAuthorization; }
    public List<Integer> getTermMonths() { return List.of(termMonths); }
    public List<String> getSkills() { return List.of(skills); }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public boolean isOpen() { return open; }
}
