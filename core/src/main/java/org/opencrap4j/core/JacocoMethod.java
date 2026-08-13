package org.opencrap4j.core;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/** A method exactly as represented by a JaCoCo class element. */
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
