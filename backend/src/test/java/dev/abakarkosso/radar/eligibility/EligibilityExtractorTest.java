package dev.abakarkosso.radar.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Each case quotes wording from a real posting in the 2026 research corpus. */
class EligibilityExtractorTest {

    private final EligibilityExtractor extractor = new EligibilityExtractor();

    @Test
    void cibcSaysCoopIsNotNeeded() {
        var e = extractor.extract("Applicants do not need to be in a registered co-op program. "
                + "You must be currently enrolled and returning to full-time studies.");
        assertThat(e.coop()).isEqualTo(CoopRequirement.NOT_REQUIRED);
        assertThat(e.mustReturnToSchool()).isTrue();
    }

    @Test
    void rodanRequiresARegisteredCoopProgram() {
        var e = extractor.extract("Currently enrolled in a recognized post-secondary co-op program. "
                + "Please provide your co-op coordinator's contact details.");
        assertThat(e.coop()).isEqualTo(CoopRequirement.REQUIRED);
    }

    @Test
    void explicitNotRequiredWinsOverACoopMention() {
        var e = extractor.extract("Open to co-op and non-co-op students. "
                + "If enrolled in a co-op program, we will coordinate with your school.");
        assertThat(e.coop()).isEqualTo(CoopRequirement.NOT_REQUIRED);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "You don\u2019t need to be enrolled in a co-op program.",
            "Applicants are not required to be enrolled in a co-op program.",
            "Co-op registration is not required for this role, but you must be enrolled in a co-op program or not.",
    })
    void recognisesOtherWaysOfSayingCoopIsNotNeeded(String text) {
        assertThat(extractor.extract(text).coop()).isEqualTo(CoopRequirement.NOT_REQUIRED);
    }

    @Test
    void typographicApostropheStillMarksGraduateOnly() {
        assertThat(extractor.extract("Currently pursuing a Master\u2019s degree in CS.").graduateDegreeRequired()).isTrue();
    }

    @Test
    void englishWordsAreNotSkills() {
        assertThat(extractor.extract("You will excel in a fast-paced team. Go beyond the basics and react quickly.")
                .skills()).isEmpty();
        assertThat(extractor.extract("Experience with Python, Go, React.js and Excel/VBA.").skills())
                .containsExactly("Python", "Go", "React", "Excel");
    }

    @Test
    void silenceMeansUnspecified() {
        assertThat(extractor.extract("Build features with our team.").coop()).isEqualTo(CoopRequirement.UNSPECIFIED);
        assertThat(extractor.extract(null).skills()).isEmpty();
    }

    @Test
    void intuitGraduationAfterTermCountsAsReturning() {
        assertThat(extractor.extract("A graduation date after the co-op term ends.").mustReturnToSchool()).isTrue();
    }

    @Test
    void collectsTermLengthsSortedAndDistinct() {
        var e = extractor.extract("This is an 8-month role. We also offer 4 month and 8 month terms.");
        assertThat(e.termMonths()).containsExactly(4, 8);
    }

    @Test
    void detectsSkillsWithoutConfusingJavaAndJavaScript() {
        var e = extractor.extract("Experience with JavaScript, React and SQL. Unit tests with Jest. "
                + "Excellent communication. Let's go build things.");
        assertThat(e.skills()).containsExactly("JavaScript", "SQL", "React", "Testing");
    }

    @Test
    void springAsASeasonIsNotTheFramework() {
        assertThat(extractor.extract("Graduating in spring/summer 2028. Summer or Spring 2027 term.").skills())
                .doesNotContain("Spring");
        assertThat(extractor.extract("Backend services in Java with Spring Boot.").skills()).contains("Spring");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Cohere Research
            "To be eligible, you must be currently pursuing a PhD in Machine Learning, NLP, or a related discipline. | true",
            // Georgian
            "Currently pursuing or recently completed a Master's or PhD degree in Computer Science. | true",
            // Stripe puts it in the title, which ingestion prepends to the description
            "PhD Data Scientist, Intern | true",
            // Undergrad wording wins over a mention of grad programs (Kinaxis, Amazon style)
            "Currently enrolled in a Bachelor's or Master's program in Computer Science. | false",
            "Pursuing an undergraduate degree; PhD students may also apply. | false",
            "Strong CS fundamentals. | false",
    })
    void detectsGraduateOnlyRoles(String text, boolean expected) {
        assertThat(extractor.extract(text).graduateDegreeRequired()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // FluidAI (SWPP funding)
            "Eligible participants must be a Canadian citizen, permanent resident or person who has been granted refugee status in Canada | CITIZEN_OR_PR",
            // Stripe
            "We will not sponsor individuals for employment visas, now or in the future for this job opening | NO_SPONSORSHIP",
            // Rockwell
            "Must be legally eligible to work in Canada for the duration of the co-op term, without requiring sponsorship now | NO_SPONSORSHIP",
            // Atlassian, P&G, Intact
            "This position is not eligible for employer sponsorship for work authorization at the time of hire | NO_SPONSORSHIP",
            "Visa sponsorship is not available for this position | NO_SPONSORSHIP",
            "Please note that Intact does not provide sponsorship or other support for immigration-related matters | NO_SPONSORSHIP",
            // Excludes nobody who can already work here
            "You must be eligible to work in Canada | UNSPECIFIED",
            // Look-alikes
            "Work with subject matter experts, mentors, executive sponsors and stakeholders | UNSPECIFIED",
            "without discrimination on the basis of race, ancestry, place of origin, colour, citizenship, religion | UNSPECIFIED",
            "Applicants who may require employer sponsorship in the future are encouraged to indicate this | UNSPECIFIED",
    })
    void readsWorkAuthorization(String text, WorkAuthorization expected) {
        assertThat(extractor.extract(text).workAuthorization()).isEqualTo(expected);
    }

    @Test
    void citizenshipRequirementOutranksNoSponsorship() {
        assertThat(extractor.extract("Canadian citizens and permanent residents only. Sponsorship is not available.")
                .workAuthorization()).isEqualTo(WorkAuthorization.CITIZEN_OR_PR);
    }

    @Test
    void graduationWindowIsExtracted() {
        assertThat(extractor.extract("graduating between August 2027 and June 2028").gradWindow())
                .isEqualTo(new GradWindow(java.time.YearMonth.of(2027, 8), java.time.YearMonth.of(2028, 6)));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Hexagon NovAtel
            "Registered in the Engineering Internship/Co-op program and have completed at least your 2nd year | REQUIRED",
            // Quebec employer: a school internship (stage) program is the co-op equivalent
            "Enrolled in an internship program and currently working toward a bachelor's degree in computer science | REQUIRED",
            // General Dynamics
            "you must be enrolled at a recognized Canadian post-secondary institution and be eligible for a co-op work term during the period | REQUIRED",
            // D2L: a preference is neither a requirement nor a statement that co-op isn't needed
            "Currently enrolled in a post-secondary program, and will not graduate prior to this co-op role (preference for students enrolled in an official co-op program) | UNSPECIFIED",
    })
    void coopWordingsFromTheEvaluationSet(String text, CoopRequirement expected) {
        assertThat(extractor.extract(text).coop()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // RBC
            "In order to be eligible you must either: Be returning back to school after the work term end-date",
            // D2L
            "Currently enrolled in a post-secondary program, and will not graduate prior to this co-op role",
    })
    void mustReturnWordingsFromTheEvaluationSet(String text) {
        assertThat(extractor.extract(text).mustReturnToSchool()).isTrue();
    }

    @Test
    void graduateOnlyWordingsFromTheEvaluationSet() {
        // Manulife quant co-op
        assertThat(extractor.extract("Advanced degree or professional designation in Math. (students in master's programs at minimum)")
                .graduateDegreeRequired()).isTrue();
        // RBC Amplify lists every level, so undergrads qualify
        assertThat(extractor.extract("graduating between August 2027 and June 2028 (College, Undergrad, Master's or PhD).")
                .graduateDegreeRequired()).isFalse();
    }

    // Code review 2026-10-02: each case is wording the earlier rules got wrong.

    @Test
    void citizenListThatAlsoAcceptsPermitHoldersIsNotCitizensOnly() {
        assertThat(extractor.extract("Must be a Canadian citizen, permanent resident, or international student "
                + "with a valid work permit.").workAuthorization()).isNotEqualTo(WorkAuthorization.CITIZEN_OR_PR);
    }

    @Test
    void pluralUndergraduatesCountsAsOpenToUndergrads() {
        assertThat(extractor.extract("Open to undergraduates and Master's or PhD students.").graduateDegreeRequired())
                .isFalse();
    }

    @Test
    void aPreferenceDoesNotCancelARequirement() {
        assertThat(extractor.extract("Must be enrolled in a co-op program. Preference given to co-op students "
                + "entering 3rd year.").coop()).isEqualTo(CoopRequirement.REQUIRED);
    }

    @ParameterizedTest
    @CsvSource({
            "Candidates must not require visa sponsorship now or in the future.",
            "The company is not able to sponsor work permits for this role.",
    })
    void moreWaysOfRefusingSponsorship(String text) {
        assertThat(extractor.extract(text).workAuthorization()).isEqualTo(WorkAuthorization.NO_SPONSORSHIP);
    }
}
