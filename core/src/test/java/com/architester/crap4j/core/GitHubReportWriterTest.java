package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class GitHubReportWriterTest {
    private static final GateConfig CONFIG =
            new GateConfig(15, 15, CoverageSelection.BRANCH_PREFERRED, false, false);

    @Test
    void summary_should_showTotalsAndHighestScores_when_mixedStatuses() {
        GateResult result = new GateResult(List.of(
                assessment("com/example/Risky", "parse", 30, MethodStatus.VIOLATION),
                assessment("com/example/Debt", "decode", 20, MethodStatus.BASELINED),
                assessment("com/example/Safe", "read", 4, MethodStatus.OK)),
                0, List.of(), List.of(), false);

        String summary = new GitHubReportWriter().summary(result, CONFIG, "example:core");

        assertThat(summary)
                .contains("## CRAP report: example:core")
                .contains("**1 violations · 1 baselined · 1 passing**")
                .contains("| 30.00 | 5 | 0.0% branch | ❌ violation | `com.example.Risky.parse` |")
                .contains("| 4.00 | 5 | 0.0% branch | ✅ passing | `com.example.Safe.read` |");
        assertThat(summary.indexOf("Risky.parse")).isLessThan(summary.indexOf("Safe.read"));
    }

    @Test
    void annotations_should_pointAtSourceLines_when_violationsPresent() {
        GateResult result = new GateResult(List.of(
                assessment("com/example/Risky", "parse", 30, MethodStatus.VIOLATION),
                assessment("com/example/Safe", "read", 4, MethodStatus.OK)),
                0, List.of(), List.of(), false);

        String annotations = new GitHubReportWriter().annotations(
                result, CONFIG, "core/src/main/java");

        assertThat(annotations)
                .contains("::error file=core/src/main/java/com/example/Risky.java,line=42")
                .contains("title=CRAP 30.00 in parse")
                .contains("CRAP 30.00 exceeds threshold 15.0")
                .doesNotContain("Safe.java");
    }

    private static MethodAssessment assessment(
            String className, String methodName, double crap, MethodStatus status) {
        ScoredMethod method = new ScoredMethod(
                className, methodName, "()V", Optional.of(className.substring(className.lastIndexOf('/') + 1)
                        + ".java"), OptionalInt.of(42), 5, 0, CoverageKind.BRANCH, crap);
        Optional<BaselineEntry> baseline = status == MethodStatus.BASELINED
                ? Optional.of(new BaselineEntry(
                        new MethodKey(className, methodName, "()V"), crap, 5))
                : Optional.empty();
        return new MethodAssessment(method, status, List.of(), baseline);
    }
}
