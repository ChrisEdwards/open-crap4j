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
        Objects.requireNonNull(coverageSelection, "coverageSelection");
    }
}
