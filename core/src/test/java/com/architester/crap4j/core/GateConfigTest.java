package com.architester.crap4j.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GateConfigTest {
    @Test
    void constructor_should_throw_when_thresholdIsNaN() {
        assertThatThrownBy(() ->
                        new GateConfig(Double.NaN, 15, CoverageSelection.BRANCH_PREFERRED, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void constructor_should_throw_when_thresholdIsInfinity() {
        assertThatThrownBy(() ->
                        new GateConfig(Double.POSITIVE_INFINITY, 15, CoverageSelection.BRANCH_PREFERRED, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void constructor_should_throw_when_thresholdIsNegative() {
        assertThatThrownBy(() ->
                        new GateConfig(-1.0, 15, CoverageSelection.BRANCH_PREFERRED, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void constructor_should_throw_when_complexityCapIsZero() {
        assertThatThrownBy(() ->
                        new GateConfig(15.0, 0, CoverageSelection.BRANCH_PREFERRED, false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("complexityCap");
    }

    @Test
    void constructor_should_accept_when_zeroThreshold() {
        new GateConfig(0.0, 1, CoverageSelection.BRANCH_PREFERRED, false, false);
    }
}
