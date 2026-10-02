package dev.abakarkosso.radar.eligibility;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Measures the extractor against hand-labelled real postings (see resources/eval/LABELLING.md).
 *
 * Precision is what matters most: a false "co-op required" hides a role a student could get.
 * The held-out split is never used while tuning patterns, so its numbers are the honest ones.
 */
class EligibilityEvalTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path LABELS = Path.of("src/test/resources/eval/labels.jsonl");
    // The site's accuracy page reads this file; the test keeps it in step with the extractor.
    private static final Path PUBLISHED = Path.of("../frontend/src/generated/accuracy.json");
    // A rule may hide postings only at 90% held-out precision; below that it may only warn.
    private static final double HIDE_PRECISION = 0.90;
    private static final double WARN_PRECISION = 0.80;

    private final EligibilityExtractor extractor = new EligibilityExtractor();

    record Example(String split, JsonNode labels, Eligibility predicted) {
    }

    /** Positive-class counts for one rule. A wrong positive counts against both precision and recall. */
    record Score(int truePositives, int falsePositives, int falseNegatives) {
        double precision() {
            return truePositives + falsePositives == 0 ? 1.0 : (double) truePositives / (truePositives + falsePositives);
        }

        double recall() {
            return truePositives + falseNegatives == 0 ? 1.0 : (double) truePositives / (truePositives + falseNegatives);
        }
    }

    /** One rule: the labelled value and the predicted value, where null means "nothing stated". */
    record Rule(String name, boolean hides, Function<JsonNode, Object> label, Function<Eligibility, Object> prediction) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("coop_required", true,
                    l -> l.get("coop_required").asBoolean() ? true : null,
                    e -> e.coop() == CoopRequirement.REQUIRED ? true : null),
            // Shows only: no held-out posting is graduate-only yet, so there is no unbiased evidence to hide on.
            new Rule("graduate_only", false,
                    l -> l.get("graduate_only").asBoolean() ? true : null,
                    e -> e.graduateDegreeRequired() ? true : null),
            new Rule("must_return", false,
                    l -> l.get("must_return").asBoolean() ? true : null,
                    e -> e.mustReturnToSchool() ? true : null),
            // Warns only: 86% held out on 2026-10-02, and its known miss (a "Preferred" section) is in the held-out set.
            new Rule("grad_window", false,
                    l -> l.get("grad_window").isNull() ? null : new GradWindow(
                            yearMonth(l.get("grad_window").get("earliest")), yearMonth(l.get("grad_window").get("latest"))),
                    Eligibility::gradWindow),
            new Rule("work_auth", true,
                    l -> "UNSPECIFIED".equals(l.get("work_auth").asString()) ? null : l.get("work_auth").asString(),
                    e -> e.workAuthorization() == WorkAuthorization.UNSPECIFIED ? null : e.workAuthorization().name()));

    @Test
    void extractorMeetsThePrecisionBarOnHeldOutPostings() throws IOException {
        List<Example> examples = Files.readAllLines(LABELS).stream()
                .filter(line -> !line.isBlank())
                .map(JSON::readTree)
                .map(n -> new Example(n.get("split").asString(), n.get("labels"), extractor.extract(n.get("text").asString())))
                .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("postings", examples.size());
        report.put("heldOut", examples.stream().filter(e -> e.split().equals("holdout")).count());
        Map<String, Object> rules = new LinkedHashMap<>();
        StringBuilder table = new StringBuilder("\nrule            split     TP  FP  FN  precision  recall\n");
        for (Rule rule : RULES) {
            Map<String, Object> bySplit = new LinkedHashMap<>();
            for (String split : List.of("holdout", "all")) {
                Score s = score(rule, examples.stream().filter(e -> split.equals("all") || e.split().equals(split)).toList());
                // Ordered on purpose: Map.of iteration order changes between JVM runs, which would make the file flap.
                Map<String, Object> counts = new LinkedHashMap<>();
                counts.put("precision", round(s.precision()));
                counts.put("recall", round(s.recall()));
                counts.put("truePositives", s.truePositives());
                counts.put("falsePositives", s.falsePositives());
                counts.put("falseNegatives", s.falseNegatives());
                bySplit.put(split, counts);
                table.append("%-15s %-8s %3d %3d %3d %10.2f %7.2f%n".formatted(rule.name(), split,
                        s.truePositives(), s.falsePositives(), s.falseNegatives(), s.precision(), s.recall()));
            }
            bySplit.put("use", rule.hides() ? "hides" : "warns");
            rules.put(rule.name(), bySplit);
        }
        report.put("rules", rules);
        System.out.println(table);

        String json = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        if (Boolean.getBoolean("updateAccuracy")) {
            Files.createDirectories(PUBLISHED.getParent());
            Files.writeString(PUBLISHED, json);
        }
        assertThat(Files.exists(PUBLISHED) ? Files.readString(PUBLISHED) : "")
                .as("Published accuracy is stale. Run: ./mvnw test -Dtest=EligibilityEvalTest -DupdateAccuracy=true")
                .isEqualTo(json);

        for (Rule rule : RULES) {
            Score holdout = score(rule, examples.stream().filter(e -> e.split().equals("holdout")).toList());
            if (rule.hides()) {
                // Precision over zero predictions is not evidence.
                assertThat(holdout.truePositives() + holdout.falsePositives())
                        .as("%s needs held-out predictions before it may hide postings", rule.name()).isPositive();
            }
            assertThat(holdout.precision()).as("%s held-out precision", rule.name())
                    .isGreaterThanOrEqualTo(rule.hides() ? HIDE_PRECISION : WARN_PRECISION);
        }
    }

    private static Score score(Rule rule, List<Example> examples) {
        int tp = 0, fp = 0, fn = 0;
        for (Example e : examples) {
            Object expected = rule.label().apply(e.labels());
            Object actual = rule.prediction().apply(e.predicted());
            if (actual != null && Objects.equals(actual, expected)) {
                tp++;
            } else {
                if (actual != null) {
                    fp++;
                }
                if (expected != null) {
                    fn++;
                }
            }
        }
        return new Score(tp, fp, fn);
    }

    private static YearMonth yearMonth(JsonNode n) {
        return n == null || n.isNull() ? null : YearMonth.parse(n.asString());
    }

    private static double round(double d) {
        return Math.round(d * 1000) / 1000.0;
    }
}
