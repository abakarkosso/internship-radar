package dev.abakarkosso.radar.posting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoleCategoryTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Software Developer Co-op (Winter 2027) | SOFTWARE",
            "Firmware Design Intern | HARDWARE",
            "Quantitative Trading Analyst Co-op | QUANT",
            "Software Engineer Intern, Machine Learning | AI_ML",
            "Product Manager Intern | PRODUCT",
            "Data Engineer Co-op | DATA",
            "Product Management Intern | PRODUCT",
            "Marketing Intern | OTHER",
            "Research Internship (Winter 2027) | AI_ML",
            "Applied Research Intern, NLP/ML/GenAI | AI_ML",
            "UX Research Intern | OTHER",
            "Market Research Intern | OTHER",
            "Software Engineer Intern, Product | SOFTWARE",
            "Software Engineer Intern, Risk | SOFTWARE",
    })
    void classifiesByTitle(String title, RoleCategory expected) {
        assertThat(RoleCategory.fromTitle(title)).isEqualTo(expected);
    }
}
