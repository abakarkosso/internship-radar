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
}
