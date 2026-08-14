package com.architester.crap4j.core;

import java.util.List;

/** Complete baseline-aware gate result. */
public record GateResult(
        List<MethodAssessment> methods,
        int excluded,
        List<SlackBaselineEntry> slackEntries,
        List<ConfigWarning> configWarnings,
        boolean requireTightBaseline) {
    public GateResult {
        methods = List.copyOf(methods);
        slackEntries = List.copyOf(slackEntries);
        configWarnings = List.copyOf(configWarnings);
    }

    public int methodViolations() {
        return (int) methods.stream()
                .filter(method -> method.status() == MethodStatus.VIOLATION)
                .count();
    }

    public int violations() {
        return methodViolations() + (requireTightBaseline ? slackEntries.size() : 0);
    }

    public int baselinedDebt() {
        return (int) methods.stream()
                .filter(method -> method.status() == MethodStatus.BASELINED)
                .count();
    }
}
