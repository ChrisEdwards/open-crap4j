package com.architester.crap4j.core;

import java.util.Objects;

/** Stored allowance for one method. */
public record BaselineEntry(MethodKey key, double crapScore, int complexity) {
    public BaselineEntry {
        Objects.requireNonNull(key, "key");
    }
}
