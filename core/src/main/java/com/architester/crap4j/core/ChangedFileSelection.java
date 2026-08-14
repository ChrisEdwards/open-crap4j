package com.architester.crap4j.core;

import java.util.Objects;

/** A JaCoCo report sliced to supplied changed files and the unmatched input count. */
public record ChangedFileSelection(JacocoReport report, int skippedFiles) {
    public ChangedFileSelection {
        Objects.requireNonNull(report, "report");
        if (skippedFiles < 0) {
            throw new IllegalArgumentException("skippedFiles must be non-negative");
        }
    }
}
