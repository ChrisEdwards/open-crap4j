package com.architester.crap4j.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A scored method plus its baseline-aware gate outcome. */
public record MethodAssessment(
        ScoredMethod method,
        MethodStatus status,
        List<GateReason> reasons,
        Optional<BaselineEntry> allowance) {
    public MethodAssessment {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(status, "status");
        reasons = List.copyOf(reasons);
        Objects.requireNonNull(allowance, "allowance");
    }
}
