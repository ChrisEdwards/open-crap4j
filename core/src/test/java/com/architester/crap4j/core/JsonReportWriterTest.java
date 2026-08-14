package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class JsonReportWriterTest {
    private static final GateConfig WHOLE_REPO =
            new GateConfig(15.0, 15, CoverageSelection.BRANCH_PREFERRED, false, false);

    @Test
    void writesTheLockedJsonShapeAsAByteStableGolden() {
        BaselineEntry allowance = new BaselineEntry(
                new MethodKey("com/example/Baselined", "work", "()V"), 18.5, 15);
        GateResult result = new GateResult(
                List.of(
                        assessment(method("com/example/Ok", "quote\"", "()V", 3, .875, 3.25),
                                MethodStatus.OK, List.of(), Optional.empty()),
                        assessment(method("com/example/Violation", "run", "(I)V", 16, .75, 20),
                                MethodStatus.VIOLATION,
                                List.of(GateReason.CRAP_OVER_THRESHOLD, GateReason.COMPLEXITY_OVER_CAP),
                                Optional.empty()),
                        assessment(method("com/example/Baselined", "work", "()V", 15, .5, 18.5),
                                MethodStatus.BASELINED,
                                List.of(GateReason.CRAP_OVER_THRESHOLD),
                                Optional.of(allowance))),
                2,
                List.of(new SlackBaselineEntry(
                        new MethodKey("com/example/Gone", "old", "()V"), SlackReason.METHOD_GONE)),
                List.of(),
                false);

        String json = new JsonReportWriter().write(
                result, WHOLE_REPO, "1.0.0", false, Optional.of("crap4j-baseline.json"));

        assertThat(json).isEqualTo("""
                {
                  "formatVersion": 1,
                  "toolVersion": "1.0.0",
                  "status": "fail",
                  "advisory": false,
                  "mode": "whole-repo",
                  "threshold": 15.0,
                  "complexityCap": 15,
                  "coverageSelection": "branch-preferred",
                  "baselineFile": "crap4j-baseline.json",
                  "summary": {
                    "methodsAnalyzed": 3,
                    "violations": 1,
                    "baselinedDebt": 1,
                    "slackEntries": 1,
                    "excluded": 2
                  },
                  "methods": [
                    {
                      "class": "com/example/Violation",
                      "method": "run",
                      "descriptor": "(I)V",
                      "sourceFile": "File.java",
                      "line": 42,
                      "complexity": 16,
                      "coverage": 0.7500,
                      "coverageKind": "branch",
                      "crap": 20.00,
                      "status": "violation",
                      "reasons": ["crap-over-threshold", "complexity-over-cap"]
                    },
                    {
                      "class": "com/example/Baselined",
                      "method": "work",
                      "descriptor": "()V",
                      "sourceFile": "File.java",
                      "line": 42,
                      "complexity": 15,
                      "coverage": 0.5000,
                      "coverageKind": "branch",
                      "crap": 18.50,
                      "status": "baselined",
                      "reasons": ["crap-over-threshold"],
                      "baseline": { "crap": 18.50, "complexity": 15 }
                    },
                    {
                      "class": "com/example/Ok",
                      "method": "quote\\\"",
                      "descriptor": "()V",
                      "sourceFile": "File.java",
                      "line": 42,
                      "complexity": 3,
                      "coverage": 0.8750,
                      "coverageKind": "branch",
                      "crap": 3.25,
                      "status": "ok"
                    }
                  ],
                  "slackBaselineEntries": [
                    {
                      "class": "com/example/Gone",
                      "method": "old",
                      "descriptor": "()V",
                      "reason": "method-gone"
                    }
                  ]
                }
                """);
    }

    @Test
    void omitsOnlyFieldsWhoseOmissionIsSpecified() {
        ScoredMethod method = new ScoredMethod(
                "Default", "run", "()V", Optional.empty(), OptionalInt.empty(),
                1, 1.0, CoverageKind.INSTRUCTION, 1.0);
        GateResult result = new GateResult(
                List.of(assessment(method, MethodStatus.OK, List.of(), Optional.empty())),
                0, List.of(), List.of(), false);

        String json = new JsonReportWriter().write(
                result, WHOLE_REPO, "1.0.0", true, Optional.empty());

        assertThat(json)
                .contains("\"status\": \"pass\"", "\"advisory\": true", "\"methodsAnalyzed\": 1")
                .doesNotContain("baselineFile", "sourceFile", "\"line\"", "reasons", "\"baseline\"");
    }

    @Test
    void advisoryAndTightBaselineStatusesFollowActualEnforcement() {
        GateResult violation = new GateResult(
                List.of(assessment(method("A", "a", "()V", 16, 0, 20), MethodStatus.VIOLATION,
                        List.of(GateReason.CRAP_OVER_THRESHOLD), Optional.empty())),
                0, List.of(), List.of(), false);
        GateResult tightSlack = new GateResult(
                List.of(), 0,
                List.of(new SlackBaselineEntry(new MethodKey("A", "a", "()V"), SlackReason.METHOD_GONE)),
                List.of(), true);

        assertThat(new JsonReportWriter().write(
                        violation, WHOLE_REPO, "1", true, Optional.empty()))
                .contains("\"status\": \"advisory\"", "\"violations\": 1");
        assertThat(new JsonReportWriter().write(
                        tightSlack,
                        new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, true, false),
                        "1", false, Optional.empty()))
                .contains("\"status\": \"fail\"", "\"violations\": 1");
    }

    @Test
    void allLockedScenariosMatchByteStableGoldensThroughScoringAndGating() throws IOException {
        Baseline debt = baseline(new MethodKey("com/example/Service", "run", "()V"), 20, 4);
        Baseline regressionAllowance =
                baseline(new MethodKey("com/example/Service", "run", "()V"), 19, 4);
        Baseline slackBaseline = new Baseline(
                1, "1.0.0", "2026-08-13T00:00:00Z",
                CoverageSelection.BRANCH_PREFERRED, 15, 15,
                List.of(
                        new BaselineEntry(new MethodKey("com/example/Clean", "done", "()V"), 20, 4),
                        new BaselineEntry(new MethodKey("com/example/Service", "run", "()V"), 21, 4)));
        Baseline changedBaseline =
                baseline(new MethodKey("com/example/Gone", "old", "()V"), 20, 4);

        List<Scenario> scenarios = List.of(
                new Scenario("first-run", scored(4, false), Optional.empty(), WHOLE_REPO, false),
                new Scenario("baselined", scored(4, false), Optional.of(debt), WHOLE_REPO, false),
                new Scenario("slack-warn", slackScoring(), Optional.of(slackBaseline), WHOLE_REPO, false),
                new Scenario("slack-tight", slackScoring(), Optional.of(slackBaseline),
                        new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, true, false), false),
                new Scenario("regression", scored(4, false), Optional.of(regressionAllowance),
                        WHOLE_REPO, false),
                new Scenario("advisory", scored(4, false), Optional.empty(), WHOLE_REPO, true),
                new Scenario("clean", scored(1, true), Optional.empty(), WHOLE_REPO, false),
                new Scenario("changed-file", scored(1, true), Optional.of(changedBaseline),
                        new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, false, true), false));

        for (Scenario scenario : scenarios) {
            GateResult result = new BaselineGate().evaluate(
                    scenario.scoring(), scenario.baseline(), scenario.config());
            String actual = new JsonReportWriter().write(
                    result,
                    scenario.config(),
                    "1.0.0",
                    scenario.advisory(),
                    scenario.baseline().map(ignored -> "crap4j-baseline.json"));

            assertThat(actual)
                    .as(scenario.name())
                    .isEqualTo(golden(scenario.name()));
        }
    }

    private static ScoringResult scored(int complexity, boolean covered) {
        JacocoMethod method = jacocoMethod("run", complexity, covered);
        JacocoClass type = new JacocoClass(
                "com/example/Service", Optional.of("Service.java"), List.of(method));
        JacocoReport report = new JacocoReport(
                "scenario", List.of(new JacocoPackage("com/example", List.of(type))));
        return new ScoringEngine().score(report, Exclusions.none());
    }

    private static ScoringResult slackScoring() {
        JacocoMethod failing = jacocoMethod("run", 4, false);
        JacocoMethod clean = jacocoMethod("done", 1, true);
        JacocoReport report = new JacocoReport(
                "scenario",
                List.of(new JacocoPackage(
                        "com/example",
                        List.of(
                                new JacocoClass("com/example/Service", Optional.of("Service.java"), List.of(failing)),
                                new JacocoClass("com/example/Clean", Optional.of("Clean.java"), List.of(clean))))));
        return new ScoringEngine().score(report, Exclusions.none());
    }

    private static JacocoMethod jacocoMethod(
            String name, int complexity, boolean covered) {
        EnumMap<CounterType, Counter> counters = new EnumMap<>(CounterType.class);
        counters.put(CounterType.COMPLEXITY, new Counter(0, complexity));
        counters.put(CounterType.BRANCH, covered ? new Counter(0, 1) : new Counter(1, 0));
        return new JacocoMethod(name, "()V", OptionalInt.of(42), counters);
    }

    private static Baseline baseline(MethodKey key, double crap, int complexity) {
        return new Baseline(
                1, "1.0.0", "2026-08-13T00:00:00Z",
                CoverageSelection.BRANCH_PREFERRED, 15, 15,
                List.of(new BaselineEntry(key, crap, complexity)));
    }

    private static String golden(String name) throws IOException {
        try (var input = JsonReportWriterTest.class.getResourceAsStream("/json-report/" + name + ".json")) {
            if (input == null) {
                throw new IOException("Missing golden: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MethodAssessment assessment(
            ScoredMethod method, MethodStatus status, List<GateReason> reasons,
            Optional<BaselineEntry> allowance) {
        return new MethodAssessment(method, status, reasons, allowance);
    }

    private static ScoredMethod method(
            String className, String methodName, String descriptor,
            int complexity, double coverage, double crap) {
        return new ScoredMethod(
                className, methodName, descriptor, Optional.of("File.java"), OptionalInt.of(42),
                complexity, coverage, CoverageKind.BRANCH, crap);
    }

    private record Scenario(
            String name,
            ScoringResult scoring,
            Optional<Baseline> baseline,
            GateConfig config,
            boolean advisory) {}
}
