package com.architester.crap4j.core;

import java.util.Objects;

/** Identity and reason for a baseline entry with slack. */
public record SlackBaselineEntry(MethodKey key, SlackReason reason) {
    public SlackBaselineEntry {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(reason, "reason");
    }
}
