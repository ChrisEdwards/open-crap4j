package com.architester.crap4j.core;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Scored methods and the number of methods excluded from scoring. */
public record ScoringResult(List<ScoredMethod> methods, int excluded) {
    public ScoringResult {
        methods = List.copyOf(methods);
    }

    /** Indexes scored methods by their stable baseline identity. */
    public Map<MethodKey, ScoredMethod> methodsByKey() {
        return methods.stream().collect(Collectors.toUnmodifiableMap(MethodKey::of, method -> method));
    }
}
