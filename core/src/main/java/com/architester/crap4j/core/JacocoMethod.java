package com.architester.crap4j.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/** A JaCoCo method after synthetic lambda bodies have been folded into source methods. */
public record JacocoMethod(
        String name,
        String descriptor,
        OptionalInt line,
        Map<CounterType, Counter> counters) {
    public JacocoMethod {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(counters, "counters");
        EnumMap<CounterType, Counter> counterCopy = new EnumMap<>(CounterType.class);
        counterCopy.putAll(counters);
        counters = Collections.unmodifiableMap(counterCopy);
    }
}
