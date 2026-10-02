package dev.abakarkosso.radar.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Every case quotes a real posting from the 2026 research corpus. */
class GradWindowTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            // between X and Y (Oracle-style)
            "Must be currently enrolled in a post-secondary institution, graduating between August 2027 and June 2028 | 2027-08 | 2028-06",
            "are graduating between Fall 2027 to Summer 2028 | 2027-12 | 2028-08",
            // X or beyond / no earlier than X (EA, Rockwell)
            "Expected graduation date is December 2027 or beyond | 2027-12 | -",
            "slated to graduate no earlier than December 2027 | 2027-12 | -",
            // by X (Atlassian)
            "returning to the program after the completion of the internship, graduating by August 2028 | - | 2028-08",
            // X or Y (Robinhood). Winter as a lower bound reads as the Canadian Jan-Apr term.
            "with an expected graduation date of Winter 2027 or Spring 2028 | 2027-04 | 2028-05",
            // Winter as an upper bound reads as December, the later of the two meanings.
            "graduating between Fall 2027 and Winter 2028 | 2027-12 | 2028-12",
            // Abbreviated months keep their full stops
            "graduating between Oct. 2027 and Sep. 2029 | 2027-10 | 2029-09",
            // before/after are exclusive
            "must be graduating before May 2027 | - | 2027-04",
            "graduating after December 2027 | 2028-01 | -",
            // reversed pair is put in order
            "graduating in Spring 2028 or Fall 2027 | 2027-12 | 2028-05",
            // a degree abbreviation before the date
            "graduating with a B.Sc. between August 2027 and June 2028 | 2027-08 | 2028-06",
            // fuzzy year (P&G)
            "pursuing a Bachelor's or Master's degree, with planned graduation in mid-late 2028 | 2028-06 | 2028-12",
            // conferral and an en dash (Amazon)
            "Expected degree conferral date between October 2027 \u2013 September 2029 | 2027-10 | 2029-09",
            // season pair (Stripe)
            "with the expectation of graduating in December 2027 or spring/summer 2028 Experience with SQL | 2027-12 | 2028-08",
            // class of (Oliver Wyman)
            "This program is open to the Class of 2028 only | 2028-01 | 2028-12",
    })
    void parsesRealPhrasings(String text, String earliest, String latest) {
        GradWindow w = GradWindow.parse(text);
        assertThat(w).isNotNull();
        assertThat(w.earliest()).isEqualTo(earliest == null ? null : YearMonth.parse(earliest));
        assertThat(w.latest()).isEqualTo(latest == null ? null : YearMonth.parse(latest));
    }

    @Test
    void noWindowWhenGraduationIsNotBoundedByADate() {
        assertThat(GradWindow.parse("Recent graduates are not eligible.")).isNull();
        assertThat(GradWindow.parse("Founded in 2008, we help graduates find jobs.")).isNull();
        assertThat(GradWindow.parse(null)).isNull();
    }

}
