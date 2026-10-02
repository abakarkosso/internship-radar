package dev.abakarkosso.radar.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.abakarkosso.radar.source.RawPosting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InternshipFilterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Software Engineer Intern | Toronto, ON | true",
            "Data Analytics Co-op (Winter 2027) | Remote, Canada | true",
            "Stagiaire en développement logiciel | Montréal, QC | true",
            "Software Engineer Intern | Toronto, Ontario | true",
            "Software Engineer Intern | Seattle, WA | false",
            "Senior Software Engineer | Toronto, ON | false",
            "Internal Tools Engineer | Toronto, ON | false",
            "Student Success Manager | Kitchener, ON | false",
            "Software Engineer, Early-Stage Products | Toronto, ON | false",
            "Stage - Développement logiciel | Québec, QC | true",
            "Student (IT, Data Science and Software) | Calgary, AB | true",
            "IT Student | Calgary, AB | true",
    })
    void keepsStudentRolesInCanada(String title, String location, boolean expected) {
        assertThat(InternshipFilter.accepts(posting(title, location))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"javascript:alert(1)", "data:text/html,hi", "ftp://x.test/job", "not a url"})
    void rejectsLinksThatAreNotHttp(String url) {
        var p = new RawPosting("test", "1", "Acme", "Software Intern", "Toronto, ON", url, "");
        assertThat(InternshipFilter.accepts(p)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Canada / San Francisco / London / Toronto | Canada, Toronto + 2 more",
            "Kitchener, ON | Kitchener, ON",
            "New York / Mississauga | Mississauga + 1 more",
    })
    void displaysCanadianLocationsFirst(String raw, String expected) {
        assertThat(InternshipFilter.displayLocation(raw)).isEqualTo(expected);
    }

    @Test
    void missingLocationIsRejected() {
        assertThat(InternshipFilter.accepts(posting("Software Intern", null))).isFalse();
    }

    private static RawPosting posting(String title, String location) {
        return new RawPosting("test", "1", "Acme", title, location, "https://x", "");
    }
}
