package com.architester.crap4j.core;

import java.util.Objects;

/** Current policy and analysis mode used by the baseline gate. */
public record GateConfig(
        double threshold,
        int complexityCap,
        CoverageSelection coverageSelection,
        boolean requireTightBaseline,
        boolean changedFileMode) {
    public GateConfig {
        if (!Double.isFinite(threshold) || threshold < 0) {
            throw new IllegalArgumentException("threshold must be a non-negative finite number: " + threshold);
        }
        if (complexityCap < 1) {
            throw new IllegalArgumentException("complexityCap must be at least 1: " + complexityCap);
        }
        Objects.requireNonNull(coverageSelection, "coverageSelection");
    }
}
