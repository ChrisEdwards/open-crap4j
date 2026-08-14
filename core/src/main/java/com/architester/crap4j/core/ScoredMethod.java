package com.architester.crap4j.core;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** A folded JaCoCo method with its calculated CRAP score. */
public record ScoredMethod(
        String className,
        String methodName,
        String descriptor,
        Optional<String> sourceFile,
        OptionalInt line,
        int complexity,
        double coverage,
        CoverageKind coverageKind,
        double crapScore) {
    public ScoredMethod {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(sourceFile, "sourceFile");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(coverageKind, "coverageKind");
    }
}
