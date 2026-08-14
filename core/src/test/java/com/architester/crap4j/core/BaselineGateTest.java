package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class BaselineGateTest {
    private static final MethodKey KEY = new MethodKey("com/example/Service", "run", "()V");
    private static final GateConfig CONFIG =
            new GateConfig(15.0, 15, CoverageSelection.BRANCH_PREFERRED, false, false);

    @Test
    void appliesTheFourGatingRules() {
        assertThat(evaluate(method(15.0, 15), null).methods().get(0).status())
                .isEqualTo(MethodStatus.OK);

        assertAssessment(
                evaluate(method(15.01, 15), null),
                MethodStatus.VIOLATION,
                GateReason.CRAP_OVER_THRESHOLD);
        assertAssessment(
                evaluate(method(15.0, 16), null),
                MethodStatus.VIOLATION,
                GateReason.COMPLEXITY_OVER_CAP);

        Baseline allowance = baseline(entry(20.0, 16));
        assertAssessment(
                evaluate(method(20.05, 16), allowance),
                MethodStatus.BASELINED,
                GateReason.CRAP_OVER_THRESHOLD,
                GateReason.COMPLEXITY_OVER_CAP);
        assertAssessment(
                evaluate(method(20.050_001, 16), allowance),
                MethodStatus.VIOLATION,
                GateReason.CRAP_REGRESSED);
        assertAssessment(
                evaluate(method(20.0, 17), allowance),
                MethodStatus.VIOLATION,
                GateReason.COMPLEXITY_REGRESSED);
    }

    @Test
    void bothRegressionRatchetsAreIndependent() {
        Baseline allowance = baseline(entry(20.0, 16));

        assertAssessment(
                evaluate(method(20.050_001, 17), allowance),
                MethodStatus.VIOLATION,
                GateReason.CRAP_REGRESSED,
                GateReason.COMPLEXITY_REGRESSED);
        assertAssessment(
                evaluate(method(19.0, 17), allowance),
                MethodStatus.VIOLATION,
                GateReason.COMPLEXITY_REGRESSED);
    }

    @Test
    void classifiesEverySlackReasonOnBothSidesOfItsBoundary() {
        MethodKey gone = new MethodKey("com/example/Gone", "run", "()V");
        Baseline baseline = baseline(
                new BaselineEntry(gone, 20.0, 16),
                entry(20.0, 16));

        GateResult presentAtLimits = evaluate(method(15.0, 15), baseline);
        assertThat(presentAtLimits.slackEntries())
                .containsExactly(
                        new SlackBaselineEntry(gone, SlackReason.METHOD_GONE),
                        new SlackBaselineEntry(KEY, SlackReason.UNDER_LIMITS));

        assertThat(evaluate(method(15.01, 16), baseline(entry(15.06, 16))).slackEntries())
                .isEmpty();
        assertThat(evaluate(method(15.01, 16), baseline(entry(15.060_001, 16))).slackEntries())
                .containsExactly(new SlackBaselineEntry(KEY, SlackReason.EXCESS_ALLOWANCE));
        assertThat(evaluate(method(20.0, 16), baseline(entry(20.05, 17))).slackEntries())
                .containsExactly(new SlackBaselineEntry(KEY, SlackReason.EXCESS_ALLOWANCE));
        assertThat(evaluate(method(20.0, 16), baseline(entry(20.05, 16))).slackEntries())
                .isEmpty();
    }

    @Test
    void tightBaselineCountsEverySlackEntryAsAViolation() {
        GateConfig strict =
                new GateConfig(15.0, 15, CoverageSelection.BRANCH_PREFERRED, true, false);
        GateResult result = new BaselineGate().evaluate(
                new ScoringResult(List.of(method(10.0, 10)), 0),
                Optional.of(baseline(entry(20.0, 16))),
                strict);

        assertThat(result.methodViolations()).isZero();
        assertThat(result.violations()).isOne();
        assertThat(result.slackEntries()).hasSize(1);
    }

    @Test
    void changedFileModeGatesPresentMethodsButDisablesSlackDetection() {
        GateConfig changedFiles =
                new GateConfig(15.0, 15, CoverageSelection.BRANCH_PREFERRED, true, true);
        Baseline baseline = baseline(
                new BaselineEntry(new MethodKey("com/example/Gone", "run", "()V"), 20.0, 16),
                entry(20.0, 16));

        GateResult result = new BaselineGate().evaluate(
                new ScoringResult(List.of(method(20.050_001, 16)), 0),
                Optional.of(baseline),
                changedFiles);

        assertThat(result.slackEntries()).isEmpty();
        assertThat(result.violations()).isOne();
        assertAssessment(result, MethodStatus.VIOLATION, GateReason.CRAP_REGRESSED);
    }

    @Test
    void rejectsSemanticConfigMismatchesAndWarnsOnPolicyChanges() {
        Baseline valid = baseline(entry(20.0, 16));
        Baseline wrongVersion = new Baseline(
                2,
                valid.toolVersion(),
                valid.generated(),
                valid.coverageSelection(),
                valid.threshold(),
                valid.complexityCap(),
                valid.entries());

        assertThatThrownBy(() -> evaluate(method(20.0, 16), wrongVersion))
                .isInstanceOf(BaselineMismatchException.class)
                .hasMessageContaining("formatVersion")
                .hasMessageContaining("re-baseline");

        Baseline wrongSelection = new Baseline(
                1,
                valid.toolVersion(),
                valid.generated(),
                CoverageSelection.INSTRUCTION_ONLY,
                valid.threshold(),
                valid.complexityCap(),
                valid.entries());
        assertThatThrownBy(() -> new BaselineGate().evaluate(
                        new ScoringResult(List.of(method(20.0, 16)), 0),
                        Optional.of(wrongSelection),
                        CONFIG))
                .isInstanceOf(BaselineMismatchException.class)
                .hasMessageContaining("coverageSelection")
                .hasMessageContaining("re-baseline");

        GateConfig changedPolicy =
                new GateConfig(10.0, 12, CoverageSelection.BRANCH_PREFERRED, false, false);
        GateResult result = new BaselineGate().evaluate(
                new ScoringResult(List.of(method(20.0, 16)), 0),
                Optional.of(valid),
                changedPolicy);
        assertThat(result.configWarnings())
                .containsExactly(ConfigWarning.THRESHOLD_MISMATCH, ConfigWarning.COMPLEXITY_CAP_MISMATCH);
    }

    private static GateResult evaluate(ScoredMethod method, Baseline baseline) {
        return new BaselineGate().evaluate(
                new ScoringResult(List.of(method), 0), Optional.ofNullable(baseline), CONFIG);
    }

    private static void assertAssessment(
            GateResult result, MethodStatus status, GateReason... reasons) {
        assertThat(result.methods()).singleElement().satisfies(assessment -> {
            assertThat(assessment.status()).isEqualTo(status);
            assertThat(assessment.reasons()).containsExactly(reasons);
        });
    }

    private static Baseline baseline(BaselineEntry... entries) {
        return new Baseline(
                1,
                "0.1.0",
                "2026-08-12T00:00:00Z",
                CoverageSelection.BRANCH_PREFERRED,
                15.0,
                15,
                List.of(entries));
    }

    private static BaselineEntry entry(double crapScore, int complexity) {
        return new BaselineEntry(KEY, crapScore, complexity);
    }

    private static ScoredMethod method(double crapScore, int complexity) {
        return new ScoredMethod(
                KEY.className(),
                KEY.methodName(),
                KEY.descriptor(),
                Optional.of("Service.java"),
                OptionalInt.of(42),
                complexity,
                0.5,
                CoverageKind.BRANCH,
                crapScore);
    }
}
