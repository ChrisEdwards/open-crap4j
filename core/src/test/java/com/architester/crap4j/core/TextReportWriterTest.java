package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class TextReportWriterTest {
    private static final GateConfig CONFIG =
            new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, false, false);

    @Test
    void s1FirstRunMatchesTheLockedGolden() throws IOException {
        assertThat(render(firstRunResult(), CONFIG, false, Optional.empty(), ReportProducer.GRADLE))
                .isEqualTo(golden("s1"));
    }

    @Test
    void s8CleanRunIsQuiet() {
        GateResult result = paddedResult(List.of(), 12, List.of(), false);

        String report = new TextReportWriter().write(
                result, CONFIG, false, Optional.of("crap4j-baseline.json"),
                ReportProducer.GRADLE, OptionalInt.empty());

        assertThat(report).isEqualTo("""
                open-crap4j  threshold 15.0  complexity cap 15  coverage branch-preferred  baseline crap4j-baseline.json

                430 methods analyzed, 12 excluded
                0 violations, 0 baselined, 0 slack entries

                PASS
                """);
    }

    @Test
    void reportNameIsRenderedAsAHeading() {
        GateResult result = paddedResult(List.of(), 0, List.of(), false);

        TextReportOutput output = new TextReportWriter().render(
                result, CONFIG, false, Optional.empty(), ReportProducer.CLI,
                OptionalInt.empty(), Optional.of("open-crap4j:core"));

        assertThat(output.standardOutput()).startsWith(
                "open-crap4j - Report for module: open-crap4j:core\n\nthreshold 15.0");
    }

    @Test
    void s2BaselinedDebtMatchesTheLockedGolden() throws IOException {
        BaselineEntry recommendation = allowance(
                "com/example/RecommendationMarkdownRenderer", "registerKnownTags", 38.5, 14);
        BaselineEntry search = allowance(
                "com/example/SearchAppVulnerabilitiesTool", "doExecute", 18.52, 15);
        GateResult result = paddedResult(List.of(
                assessment("com/example/RecommendationMarkdownRenderer", "registerKnownTags",
                        "RecommendationMarkdownRenderer.java", 41, 14, .5, 38.5,
                        MethodStatus.BASELINED, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.of(recommendation)),
                assessment("com/example/SearchAppVulnerabilitiesTool", "doExecute",
                        "SearchAppVulnerabilitiesTool.java", 88, 15, .75, 18.515625,
                        MethodStatus.BASELINED, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.of(search))),
                12, List.of(), false);

        assertThat(render(result, CONFIG, false, Optional.of("crap4j-baseline.json"), ReportProducer.GRADLE))
                .isEqualTo(golden("s2"));
    }

    @Test
    void s3SlackWarnMatchesTheLockedGolden() throws IOException {
        GateResult result = slackResult(false);

        assertThat(render(result, CONFIG, false, Optional.of("crap4j-baseline.json"), ReportProducer.GRADLE))
                .isEqualTo(golden("s3"));
    }

    @Test
    void s4SlackTightMatchesTheLockedGolden() throws IOException {
        GateResult result = slackResult(true);
        GateConfig tight = new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, true, false);

        assertThat(render(result, tight, false, Optional.of("crap4j-baseline.json"), ReportProducer.GRADLE))
                .isEqualTo(golden("s4"));
    }

    @Test
    void s5RegressionMatchesTheLockedGolden() throws IOException {
        BaselineEntry search = allowance(
                "com/example/SearchAppVulnerabilitiesTool", "doExecute", 18.52, 15);
        BaselineEntry recommendation = allowance(
                "com/example/RecommendationMarkdownRenderer", "registerKnownTags", 38.5, 14);
        GateResult result = paddedResult(List.of(
                assessment("com/example/SearchAppVulnerabilitiesTool", "doExecute",
                        "SearchAppVulnerabilitiesTool.java", 88, 16, .706, 22.51,
                        MethodStatus.VIOLATION,
                        List.of(GateReason.CRAP_REGRESSED, GateReason.COMPLEXITY_REGRESSED), Optional.of(search)),
                assessment("com/example/RecommendationMarkdownRenderer", "registerKnownTags",
                        "RecommendationMarkdownRenderer.java", 41, 14, .5, 38.5,
                        MethodStatus.BASELINED, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.of(recommendation))),
                12, List.of(), false);

        assertThat(render(result, CONFIG, false, Optional.of("crap4j-baseline.json"), ReportProducer.GRADLE))
                .isEqualTo(golden("s5"));
    }

    @Test
    void s6CoupledThresholdWarningsMatchTheLockedGoldens() throws IOException {
        GateResult empty = new GateResult(List.of(), 0, List.of(), List.of(), false);
        GateConfig unreachable =
                new GateConfig(250, 15, CoverageSelection.BRANCH_PREFERRED, false, false);
        GateConfig hidden =
                new GateConfig(10, 15, CoverageSelection.BRANCH_PREFERRED, false, false);

        assertThat(render(empty, unreachable, false, Optional.empty(), ReportProducer.GRADLE))
                .isEqualTo(golden("s6-unreachable"));
        assertThat(render(empty, hidden, false, Optional.empty(), ReportProducer.GRADLE))
                .isEqualTo(golden("s6-hidden-cap"));

        TextReportOutput output = new TextReportWriter().render(
                empty, unreachable, false, Optional.empty(),
                ReportProducer.GRADLE, OptionalInt.empty());
        assertThat(output.standardOutput()).isEqualTo(
                "open-crap4j  threshold 250.0  complexity cap 15  coverage branch-preferred  no baseline\n");
        assertThat(output.diagnostics()).startsWith("WARNING: threshold 250.0");
    }

    @Test
    void s7AdvisoryMatchesTheLockedGolden() throws IOException {
        GateResult result = firstRunResult();

        assertThat(render(result, CONFIG, true, Optional.empty(), ReportProducer.GRADLE))
                .isEqualTo(golden("s7"));
    }

    @Test
    void showPassingIsOptInAndCliWordingNamesVerbs() {
        MethodAssessment passing = assessment(
                "com/example/Service", "healthy", "Service.java", 10, 3, 1, 3,
                MethodStatus.OK, List.of(), Optional.empty());
        GateResult result = new GateResult(List.of(passing), 0, List.of(), List.of(), false);

        String defaultReport = render(result, CONFIG, false, Optional.empty(), ReportProducer.CLI);
        String withPassing = new TextReportWriter().write(
                result, CONFIG, false, Optional.empty(), ReportProducer.CLI, OptionalInt.of(1));

        assertThat(defaultReport).doesNotContain("Passing methods");
        assertThat(withPassing)
                .contains("Passing methods (1) — highest CRAP scores still within the limits")
                .contains("Service.healthy");

        GateResult violation = new GateResult(List.of(assessment(
                "com/example/Service", "bad", "Service.java", 11, 4, 0, 20,
                MethodStatus.VIOLATION, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.empty())),
                0, List.of(), List.of(), false);
        assertThat(render(violation, CONFIG, false, Optional.empty(), ReportProducer.CLI))
                .contains("with baseline.")
                .doesNotContain("crapBaseline");

        GateResult slack = slackResult(false);
        assertThat(render(
                        slack, CONFIG, false, Optional.of("baseline.json"), ReportProducer.CLI))
                .contains("lock in the progress with tighten.")
                .doesNotContain("crapBaselineTighten");

        BaselineEntry allowance = allowance("com/example/Service", "bad", 18, 4);
        GateResult regression = new GateResult(List.of(assessment(
                "com/example/Service", "bad", "Service.java", 11, 5, 0, 30,
                MethodStatus.VIOLATION, List.of(GateReason.CRAP_REGRESSED), Optional.of(allowance))),
                0, List.of(), List.of(), false);
        assertThat(render(
                        regression, CONFIG, false, Optional.of("baseline.json"), ReportProducer.CLI))
                .contains("re-admit the new debt with baseline (reviewed")
                .doesNotContain("crapBaseline");
    }

    private static GateResult firstRunResult() {
        return paddedResult(List.of(
                assessment("com/example/RecommendationMarkdownRenderer", "registerKnownTags",
                        "RecommendationMarkdownRenderer.java", 41, 14, .5, 38.5,
                        MethodStatus.VIOLATION, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.empty()),
                assessment("com/example/SearchAppVulnerabilitiesTool", "doExecute",
                        "SearchAppVulnerabilitiesTool.java", 88, 15, .75, 18.515625,
                        MethodStatus.VIOLATION, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.empty())),
                12, List.of(), false);
    }

    private static GateResult slackResult(boolean tight) {
        BaselineEntry search = allowance(
                "com/example/SearchAppVulnerabilitiesTool", "doExecute", 18.52, 15);
        BaselineEntry recommendation = allowance(
                "com/example/RecommendationMarkdownRenderer", "registerKnownTags", 38.5, 14);
        MethodAssessment baselined = assessment(
                "com/example/SearchAppVulnerabilitiesTool", "doExecute",
                "SearchAppVulnerabilitiesTool.java", 88, 15, .833, 16.05,
                MethodStatus.BASELINED, List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.of(search));
        MethodAssessment improved = assessment(
                "com/example/RecommendationMarkdownRenderer", "registerKnownTags",
                "RecommendationMarkdownRenderer.java", 41, 10, 1, 10,
                MethodStatus.OK, List.of(), Optional.of(recommendation));
        return paddedResult(
                List.of(baselined, improved),
                12,
                List.of(
                        new SlackBaselineEntry(recommendation.key(), SlackReason.UNDER_LIMITS),
                        new SlackBaselineEntry(search.key(), SlackReason.EXCESS_ALLOWANCE)),
                tight);
    }

    private static GateResult paddedResult(
            List<MethodAssessment> significant,
            int excluded,
            List<SlackBaselineEntry> slack,
            boolean tight) {
        List<MethodAssessment> methods = new ArrayList<>(significant);
        for (int index = methods.size(); index < 430; index++) {
            methods.add(assessment(
                    "com/example/Passing" + index, "ok", "Passing.java", 1,
                    1, 1, 1, MethodStatus.OK, List.of(), Optional.empty()));
        }
        return new GateResult(methods, excluded, slack, List.of(), tight);
    }

    private static BaselineEntry allowance(
            String className, String methodName, double crap, int complexity) {
        return new BaselineEntry(new MethodKey(className, methodName, "()V"), crap, complexity);
    }

    private static String render(
            GateResult result,
            GateConfig config,
            boolean advisory,
            Optional<String> baseline,
            ReportProducer producer) {
        return new TextReportWriter().write(
                result, config, advisory, baseline, producer, OptionalInt.empty());
    }

    private static String golden(String name) throws IOException {
        try (var input = TextReportWriterTest.class.getResourceAsStream("/text-report/" + name + ".txt")) {
            if (input == null) {
                throw new IOException("Missing golden " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MethodAssessment assessment(
            String className,
            String methodName,
            String sourceFile,
            int line,
            int complexity,
            double coverage,
            double crap,
            MethodStatus status,
            List<GateReason> reasons,
            Optional<BaselineEntry> allowance) {
        ScoredMethod method = new ScoredMethod(
                className, methodName, "()V", Optional.of(sourceFile), OptionalInt.of(line),
                complexity, coverage, CoverageKind.BRANCH, crap);
        return new MethodAssessment(method, status, reasons, allowance);
    }
}
