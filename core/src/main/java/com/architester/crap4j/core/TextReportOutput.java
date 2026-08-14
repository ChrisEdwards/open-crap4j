package com.architester.crap4j.core;

import java.util.Objects;

/** Text report split into the normal output and diagnostic channels. */
public record TextReportOutput(String standardOutput, String diagnostics) {
    public TextReportOutput {
        Objects.requireNonNull(standardOutput, "standardOutput");
        Objects.requireNonNull(diagnostics, "diagnostics");
    }

    /** Combines channels in the order used by the locked human-readable examples. */
    public String combined() {
        if (diagnostics.isEmpty()) {
            return standardOutput;
        }
        int firstLineEnd = standardOutput.indexOf('\n');
        if (firstLineEnd < 0) {
            return standardOutput + "\n\n" + diagnostics;
        }
        String firstLine = standardOutput.substring(0, firstLineEnd + 1);
        String remainder = standardOutput.substring(firstLineEnd + 1);
        if (remainder.isEmpty()) {
            return firstLine + "\n" + diagnostics;
        }
        return firstLine + "\n" + diagnostics + remainder;
    }
}
