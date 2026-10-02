package dev.abakarkosso.radar.posting;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

import dev.abakarkosso.radar.eligibility.CoopRequirement;
import dev.abakarkosso.radar.eligibility.WorkAuthorization;

/** Feed row. Leaves out the description, which is large and not needed for the list view. */
public record PostingSummary(
        Long id, String company, String title, String location, String url,
        RoleCategory category, CoopRequirement coopRequirement, boolean mustReturnToSchool,
        boolean graduateDegreeRequired, YearMonth gradEarliest, YearMonth gradLatest,
        WorkAuthorization workAuthorization, List<Integer> termMonths, List<String> skills, Instant firstSeenAt) {

    static PostingSummary of(Posting p) {
        return new PostingSummary(p.getId(), p.getCompany(), p.getTitle(), p.getLocation(), p.getUrl(),
                p.getCategory(), p.getCoopRequirement(), p.isMustReturnToSchool(), p.isGraduateDegreeRequired(),
                p.getGradEarliest(), p.getGradLatest(), p.getWorkAuthorization(),
                p.getTermMonths(), p.getSkills(), p.getFirstSeenAt());
    }
}
