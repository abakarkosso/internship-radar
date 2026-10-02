package dev.abakarkosso.radar.eligibility;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Reads eligibility rules and skills out of a job description.
 *
 * Rule-based on purpose: every rule is traceable to wording seen in real postings,
 * and each one is pinned by a unit test. An LLM fallback can come later for the misses.
 */
@Component
public class EligibilityExtractor {

    private static final int I = Pattern.CASE_INSENSITIVE;

    // "Applicants do not need to be in a registered co-op program" (CIBC) beats any co-op mention.
    private static final Pattern COOP_NOT_REQUIRED = Pattern.compile(
            "(do(es)? not|don't) need to be (enrolled |registered )?in an? (registered |recognized )?co-?op"
            + "|not required to be (enrolled |registered )?in an? (registered |recognized )?co-?op"
            + "|co-?op (enrolment|enrollment|registration) is not required"
            + "|co-?op (and|or) non-co-?op|non-co-?op (students|candidates|interns)", I);

    private static final Pattern COOP_REQUIRED = Pattern.compile(
            "(enrolled|registered) in (a|an|your)\\s[^.]{0,80}co-?op (program|term)"
            + "|approved co-?op work term|co-?op coordinator|scheduled co-?op work term", I);

    private static final Pattern MUST_RETURN = Pattern.compile(
            "return(ing)? to (school|full-time studies|studies|your studies|their studies|an academic)"
            + "|graduation date after the co-?op term|remaining in (your|their) studies"
            + "|recent graduates are not eligible", I);

    // "currently pursuing a PhD" (Cohere), "a Master's or PhD degree" (Georgian), "PhD Data Scientist" (Stripe title).
    private static final Pattern GRAD_ONLY = Pattern.compile(
            "pursuing,?( or [^.]{0,40})? an? (Master'?s|PhD|Ph\\.D|graduate degree)"
            + "|\\bMaster'?s or PhD\\b|\\bPhD (student|candidate|intern|data scientist|researcher)", I);

    private static final Pattern UNDERGRAD_OK = Pattern.compile(
            "bachelor'?s|undergraduate|\\bBSc\\b|\\bB\\.Sc", I);

    private static final Pattern TERM_MONTHS = Pattern.compile("\\b(4|8|12|16)[- ]?months?\\b", I);

    private static final Map<String, Pattern> SKILLS = new LinkedHashMap<>();
    static {
        SKILLS.put("Python", Pattern.compile("\\bpython\\b", I));
        SKILLS.put("Java", Pattern.compile("\\bjava\\b(?!\\s*script)", I));
        SKILLS.put("JavaScript", Pattern.compile("javascript", I));
        SKILLS.put("TypeScript", Pattern.compile("typescript", I));
        SKILLS.put("C++", Pattern.compile("c\\+\\+", I));
        SKILLS.put("C#", Pattern.compile("c#|\\.net\\b", I));
        // Case-sensitive and list-shaped: "Go beyond" and "go" are English, "Python, Go," is the language.
        SKILLS.put("Go", Pattern.compile("(?i:\\bgolang\\b)|(?<=[,(/]\\s?)Go\\b|\\bGo(?=\\s*[,/)])"));
        SKILLS.put("SQL", Pattern.compile("\\bsql\\b", I));
        SKILLS.put("React", Pattern.compile("\\bReact(\\.?js)?\\b")); // case-sensitive: "react" is a verb
        // Case-sensitive and qualified: plain "spring" is usually a season ("Spring 2028").
        SKILLS.put("Spring", Pattern.compile("Spring ?(Boot|Framework|MVC|Cloud|Data)|Java/Spring"));
        SKILLS.put("AWS", Pattern.compile("\\baws\\b|amazon web services", I));
        SKILLS.put("Azure", Pattern.compile("\\bazure\\b", I));
        SKILLS.put("GCP", Pattern.compile("\\bgcp\\b|google cloud", I));
        SKILLS.put("Docker", Pattern.compile("\\bdocker\\b", I));
        SKILLS.put("Kubernetes", Pattern.compile("kubernetes|\\bk8s\\b", I));
        SKILLS.put("CI/CD", Pattern.compile("ci/cd|continuous integration|github actions|jenkins", I));
        SKILLS.put("Testing", Pattern.compile("unit test|pytest|junit|\\bjest\\b|test automation", I));
        SKILLS.put("Machine learning", Pattern.compile("machine learning|\\bml\\b|pytorch|tensorflow", I));
        SKILLS.put("LLMs", Pattern.compile("\\bllms?\\b|large language|generative ai|\\brag\\b", I));
        SKILLS.put("Power BI", Pattern.compile("power ?bi|tableau", I));
        SKILLS.put("Excel", Pattern.compile("\\bExcel\\b|\\bVBA\\b")); // case-sensitive: "excel" is a verb
    }

    public Eligibility extract(String text) {
        // Boards often use typographic apostrophes ("don’t", "Master’s"); the patterns are written with '.
        String t = text == null ? "" : text.replace('\u2019', '\'');

        CoopRequirement coop = COOP_NOT_REQUIRED.matcher(t).find() ? CoopRequirement.NOT_REQUIRED
                : COOP_REQUIRED.matcher(t).find() ? CoopRequirement.REQUIRED
                : CoopRequirement.UNSPECIFIED;

        TreeSet<Integer> months = new TreeSet<>();
        Matcher m = TERM_MONTHS.matcher(t);
        while (m.find()) {
            months.add(Integer.parseInt(m.group(1)));
        }

        List<String> skills = SKILLS.entrySet().stream()
                .filter(e -> e.getValue().matcher(t).find())
                .map(Map.Entry::getKey)
                .toList();

        boolean graduateOnly = GRAD_ONLY.matcher(t).find() && !UNDERGRAD_OK.matcher(t).find();

        return new Eligibility(coop, MUST_RETURN.matcher(t).find(), graduateOnly, List.copyOf(months), skills);
    }

}
