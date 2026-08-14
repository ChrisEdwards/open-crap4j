package com.architester.crap4j.core;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** A complete baseline file. */
public record Baseline(
        int formatVersion,
        String toolVersion,
        String generated,
        CoverageSelection coverageSelection,
        double threshold,
        int complexityCap,
        List<BaselineEntry> entries) {
    public Baseline {
        Objects.requireNonNull(toolVersion, "toolVersion");
        Objects.requireNonNull(generated, "generated");
        Objects.requireNonNull(coverageSelection, "coverageSelection");
        entries = entries.stream()
                .sorted(Comparator.comparing(BaselineEntry::key))
                .toList();
    }
}
