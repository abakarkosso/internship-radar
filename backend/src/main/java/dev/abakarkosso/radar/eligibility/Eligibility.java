package dev.abakarkosso.radar.eligibility;

import java.util.List;

/** What a posting demands of the applicant, as read from its description. */
public record Eligibility(
        CoopRequirement coop,
        boolean mustReturnToSchool,
        boolean graduateDegreeRequired,
        GradWindow gradWindow,
        WorkAuthorization workAuthorization,
        boolean securityClearanceRequired,
        List<Integer> termMonths,
        List<String> skills) {
}
