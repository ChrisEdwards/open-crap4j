package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ThresholdWarningsTest {
    @Test
    void warnsWhenTheThresholdEqualsTheWorstScoreAllowedByTheCap() {
        assertThat(ThresholdWarnings.compute(240.0, 15))
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning.kind()).isEqualTo(ThresholdWarning.Kind.UNREACHABLE_GATE);
                    assertThat(warning.message())
                            .isEqualTo(
                                    """
                                    WARNING: threshold 240.0 is unreachable under complexity cap 15.
                                    The worst score a cc 15 method can reach is 240 (fully uncovered).
                                    The CRAP gate can never fire, this run is a plain complexity check.
                                    Suggested pairing: threshold 15.0 (threshold = cap demands full coverage at the cap).""");
                });

        assertThat(ThresholdWarnings.compute(Math.nextDown(240.0), 15)).isEmpty();
    }

    @Test
    void warnsOnlyWhenTheThresholdIsStrictlyBelowTheCap() {
        assertThat(ThresholdWarnings.compute(Math.nextDown(15.0), 15))
                .singleElement()
                .satisfies(warning -> {
                    assertThat(warning.kind()).isEqualTo(ThresholdWarning.Kind.HIDDEN_COMPLEXITY_CAP);
                    assertThat(warning.message())
                            .isEqualTo(
                                    """
                                    WARNING: threshold 15.0 is below complexity cap 15.
                                    CRAP >= cc always, so every method over cc 14 fails regardless of coverage.
                                    The threshold is acting as a hidden complexity cap of 14.
                                    Suggested pairing: complexity cap 14, or threshold 15.0.""");
                });

        assertThat(ThresholdWarnings.compute(15.0, 15)).isEmpty();
    }
}
