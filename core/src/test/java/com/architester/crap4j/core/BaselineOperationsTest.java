package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class BaselineOperationsTest {
    private static final GateConfig CONFIG =
            new GateConfig(15.0, 15, CoverageSelection.BRANCH_PREFERRED, false, false);
    private static final String NOW = "2026-08-13T12:00:00Z";

    @Test
    void rebaseline_should_storeOnlyDebt_when_mixOfPassingAndFailing() {
        ScoredMethod passing = method("passing", 15.0, 15);
        ScoredMethod debt = method("debt", 18.515, 16);

        Baseline baseline = new BaselineOperations()
                .rebaseline(new ScoringResult(List.of(passing, debt), 0), CONFIG, "0.1.0", NOW);

        assertThat(baseline.formatVersion()).isEqualTo(1);
        assertThat(baseline.toolVersion()).isEqualTo("0.1.0");
        assertThat(baseline.generated()).isEqualTo(NOW);
        assertThat(baseline.entries())
                .containsExactly(new BaselineEntry(MethodKey.of(debt), 18.52, 16));
    }

    @Test
    void tighten_should_removeSlackEntries_when_goneAndPassing() {
        ScoredMethod improved = method("improved", 20.0, 16);
        ScoredMethod passing = method("passing", 10.0, 10);
        ScoredMethod newDebt = method("newDebt", 30.0, 20);
        Baseline original = baseline(
                entry("gone", 30.0, 20),
                entry("improved", 25.0, 18),
                entry("passing", 20.0, 16));

        Baseline tightened = new BaselineOperations().tighten(
                original,
                new ScoringResult(List.of(improved, passing, newDebt), 0),
                CONFIG,
                "0.2.0",
                NOW);

        assertThat(tightened.generated()).isEqualTo(NOW);
        assertThat(tightened.toolVersion()).isEqualTo("0.2.0");
        assertThat(tightened.entries())
                .containsExactly(new BaselineEntry(MethodKey.of(improved), 20.0, 16));
    }

    @Test
    void tighten_should_returnSameBaseline_when_atEpsilonBoundary() {
        ScoredMethod current = method("run", 20.0, 16);
        Baseline exactBoundary = baseline(entry("run", 20.05, 16));

        Baseline unchanged = new BaselineOperations().tighten(
                exactBoundary, new ScoringResult(List.of(current), 0), CONFIG, "0.2.0", NOW);

        assertThat(unchanged).isSameAs(exactBoundary);
        assertThat(unchanged.generated()).isEqualTo(exactBoundary.generated());

        Baseline pastBoundary = baseline(entry("run", 20.050_001, 16));
        assertThat(new BaselineOperations()
                        .tighten(
                                pastBoundary,
                                new ScoringResult(List.of(current), 0),
                                CONFIG,
                                "0.2.0",
                                NOW)
                        .entries())
                .containsExactly(entry("run", 20.0, 16));
    }

    @Test
    void tighten_should_lockInImprovement_when_scoreLaterDrifts() {
        ScoredMethod originalDebt = method("run", 92.0, 16);
        Baseline original = baseline(entry("run", 92.0, 16));
        ScoredMethod improved = method("run", 50.0, 16);

        GateResult beforeTighten = gate(method("run", 90.0, 16), original);
        Baseline tightened = new BaselineOperations().tighten(
                original, new ScoringResult(List.of(improved), 0), CONFIG, "0.2.0", NOW);
        GateResult afterTighten = gate(method("run", 90.0, 16), tightened);

        assertThat(originalDebt.crapScore()).isEqualTo(92.0);
        assertThat(beforeTighten.methods().get(0).status()).isEqualTo(MethodStatus.BASELINED);
        assertThat(tightened.entries().get(0).crapScore()).isEqualTo(50.0);
        assertThat(afterTighten.methods().get(0).status()).isEqualTo(MethodStatus.VIOLATION);
        assertThat(afterTighten.methods().get(0).reasons())
                .containsExactly(GateReason.CRAP_REGRESSED);
    }

    @Test
    void tighten_should_removeNewlyUnderLimitEntries_when_thresholdRaised() {
        ScoredMethod underNewThreshold = method("fixed", 20.0, 14);
        ScoredMethod stillOver = method("debt", 30.0, 16);
        Baseline original = baseline(
                entry("fixed", 20.0, 14),
                entry("debt", 30.0, 16));
        GateConfig raised = new GateConfig(25.0, 15, CoverageSelection.BRANCH_PREFERRED, false, false);

        Baseline tightened = new BaselineOperations().tighten(
                original, new ScoringResult(List.of(underNewThreshold, stillOver), 0),
                raised, "0.2.0", NOW);

        assertThat(tightened.entries())
                .containsExactly(entry("debt", 30.0, 16));
        assertThat(tightened.threshold()).isEqualTo(25.0);
    }

    @Test
    void tighten_should_removeNewlyUnderLimitEntries_when_complexityCapRaised() {
        ScoredMethod underNewCap = method("fixed", 14.0, 16);
        ScoredMethod stillOver = method("debt", 30.0, 20);
        Baseline original = baseline(
                entry("fixed", 14.0, 16),
                entry("debt", 30.0, 20));
        GateConfig raised = new GateConfig(15.0, 20, CoverageSelection.BRANCH_PREFERRED, false, false);

        Baseline tightened = new BaselineOperations().tighten(
                original, new ScoringResult(List.of(underNewCap, stillOver), 0),
                raised, "0.2.0", NOW);

        assertThat(tightened.entries())
                .containsExactly(entry("debt", 30.0, 20));
        assertThat(tightened.complexityCap()).isEqualTo(20);
    }

    @Test
    void tighten_should_neverAddEntries_when_policyDiffers() {
        ScoredMethod existing = method("baselined", 20.0, 16);
        ScoredMethod newViolation = method("newDebt", 18.0, 14);
        Baseline original = baseline(entry("baselined", 20.0, 16));
        GateConfig lowered = new GateConfig(10.0, 15, CoverageSelection.BRANCH_PREFERRED, false, false);

        Baseline tightened = new BaselineOperations().tighten(
                original, new ScoringResult(List.of(existing, newViolation), 0),
                lowered, "0.2.0", NOW);

        assertThat(tightened.entries()).hasSize(1);
        assertThat(tightened.entries().get(0).key()).isEqualTo(MethodKey.of(existing));
        assertThat(tightened.threshold()).isEqualTo(10.0);
    }

    @Test
    void rebaseline_should_throw_when_changedFileMode() {
        GateConfig changedFiles =
                new GateConfig(15.0, 15, CoverageSelection.BRANCH_PREFERRED, false, true);
        ScoringResult scoring = new ScoringResult(List.of(method("run", 20.0, 16)), 0);

        assertThatThrownBy(() -> new BaselineOperations()
                        .rebaseline(scoring, changedFiles, "0.1.0", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole-repo");
        assertThatThrownBy(() -> new BaselineOperations()
                        .tighten(
                                baseline(entry("run", 20.0, 16)),
                                scoring,
                                changedFiles,
                                "0.2.0",
                                NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole-repo");
    }

    private static GateResult gate(ScoredMethod method, Baseline baseline) {
        return new BaselineGate().evaluate(
                new ScoringResult(List.of(method), 0), Optional.of(baseline), CONFIG);
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

    private static BaselineEntry entry(String methodName, double crapScore, int complexity) {
        return new BaselineEntry(
                new MethodKey("com/example/Service", methodName, "()V"), crapScore, complexity);
    }

    private static ScoredMethod method(String methodName, double crapScore, int complexity) {
        return new ScoredMethod(
                "com/example/Service",
                methodName,
                "()V",
                Optional.of("Service.java"),
                OptionalInt.of(42),
                complexity,
                0.5,
                CoverageKind.BRANCH,
                crapScore);
    }
}
