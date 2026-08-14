package com.architester.crap4j.core;

import java.util.Objects;

/** A configuration warning caused by a mismatched CRAP threshold and complexity cap. */
public record ThresholdWarning(Kind kind, String message) {
    public ThresholdWarning {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
    }

    public enum Kind {
        UNREACHABLE_GATE,
        HIDDEN_COMPLEXITY_CAP
    }
}
