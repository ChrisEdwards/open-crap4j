package com.architester.crap4j.core;

import java.util.List;

/** Scored methods and the number of methods excluded from scoring. */
public record ScoringResult(List<ScoredMethod> methods, int excluded) {
    public ScoringResult {
        methods = List.copyOf(methods);
    }
}
