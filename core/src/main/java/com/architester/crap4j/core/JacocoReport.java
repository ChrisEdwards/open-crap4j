package com.architester.crap4j.core;

import java.util.List;
import java.util.Objects;

/** The package hierarchy parsed from one JaCoCo XML report. */
public record JacocoReport(String name, List<JacocoPackage> packages) {
    public JacocoReport {
        Objects.requireNonNull(name, "name");
        packages = List.copyOf(packages);
    }
}
