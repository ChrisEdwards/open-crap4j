package com.architester.crap4j.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Scored methods and the number of methods excluded from scoring. */
public record ScoringResult(List<ScoredMethod> methods, int excluded) {
    public ScoringResult {
        methods = List.copyOf(methods);
    }

    /** Indexes scored methods by their stable baseline identity. */
    public Map<MethodKey, ScoredMethod> methodsByKey() {
        Map<MethodKey, ScoredMethod> map = new LinkedHashMap<>();
        for (ScoredMethod method : methods) {
            MethodKey key = MethodKey.of(method);
            ScoredMethod previous = map.putIfAbsent(key, method);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate method identity: " + key.className()
                                + "." + key.methodName() + key.descriptor()
                                + " — aggregated JaCoCo reports with overlapping modules are not supported");
            }
        }
        return Collections.unmodifiableMap(map);
    }
}
