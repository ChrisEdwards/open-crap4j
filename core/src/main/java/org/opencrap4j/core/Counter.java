package org.opencrap4j.core;

/** A JaCoCo counter's missed and covered values. */
public record Counter(int missed, int covered) {
    public Counter {
        if (missed < 0 || covered < 0) {
            throw new IllegalArgumentException("Counter values must be non-negative");
        }
    }
}
