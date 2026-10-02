package dev.abakarkosso.radar.posting;

import java.time.Instant;
import java.util.List;

import dev.abakarkosso.radar.eligibility.CoopRequirement;

/** Feed row. Leaves out the description, which is large and not needed for the list view. */
public record PostingSummary(
        Long id, String company, String title, String location, String url,
        RoleCategory category, CoopRequirement coopRequirement, boolean mustReturnToSchool,
        boolean graduateDegreeRequired, List<Integer> termMonths, List<String> skills, Instant firstSeenAt) {

    static PostingSummary of(Posting p) {
        return new PostingSummary(p.getId(), p.getCompany(), p.getTitle(), p.getLocation(), p.getUrl(),
                p.getCategory(), p.getCoopRequirement(), p.isMustReturnToSchool(), p.isGraduateDegreeRequired(),
                p.getTermMonths(), p.getSkills(), p.getFirstSeenAt());
    }
}
