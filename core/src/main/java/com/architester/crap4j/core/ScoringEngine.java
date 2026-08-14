package com.architester.crap4j.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Turns a folded JaCoCo report into scored methods. */
public final class ScoringEngine {
    private static final Comparator<ScoredMethod> REPORT_ORDER =
            Comparator.comparingDouble(ScoredMethod::crap)
                    .reversed()
                    .thenComparing(ScoredMethod::className)
                    .thenComparing(ScoredMethod::methodName)
                    .thenComparing(ScoredMethod::descriptor);

    public ScoringResult score(JacocoReport report, Exclusions exclusions) {
        List<ScoredMethod> scored = new ArrayList<>();
        int excluded = 0;
        for (JacocoPackage jacocoPackage : report.packages()) {
            for (JacocoClass jacocoClass : jacocoPackage.classes()) {
                for (JacocoMethod method : jacocoClass.methods()) {
                    if (method.name().equals("<clinit>")
                            || exclusions.excludes(jacocoPackage.name(), jacocoClass)) {
                        excluded++;
                    } else {
                        scored.add(score(jacocoClass, method));
                    }
                }
            }
        }
        scored.sort(REPORT_ORDER);
        return new ScoringResult(scored, excluded);
    }

    private static ScoredMethod score(JacocoClass jacocoClass, JacocoMethod method) {
        Map<CounterType, Counter> counters = method.counters();
        Counter complexityCounter = requiredCounter(counters, CounterType.COMPLEXITY, method);
        int complexity = complexityCounter.missed() + complexityCounter.covered();
        Counter branch = counters.get(CounterType.BRANCH);
        CoverageKind coverageKind = branch != null && total(branch) > 0
                ? CoverageKind.BRANCH
                : CoverageKind.INSTRUCTION;
        Counter coverageCounter = coverageKind == CoverageKind.BRANCH
                ? branch
                : requiredCounter(counters, CounterType.INSTRUCTION, method);
        double coverage = total(coverageCounter) == 0
                ? 0.0
                : (double) coverageCounter.covered() / total(coverageCounter);
        double uncovered = 1.0 - coverage;
        double crap = (double) complexity * complexity * uncovered * uncovered * uncovered
                + complexity;
        return new ScoredMethod(
                jacocoClass.name(),
                method.name(),
                method.descriptor(),
                jacocoClass.sourceFile(),
                method.line(),
                complexity,
                coverage,
                coverageKind,
                crap);
    }

    private static Counter requiredCounter(
            Map<CounterType, Counter> counters, CounterType type, JacocoMethod method) {
        Counter counter = counters.get(type);
        if (counter == null) {
            throw new IllegalArgumentException(
                    "Method " + method.name() + method.descriptor() + " has no " + type + " counter");
        }
        return counter;
    }

    private static int total(Counter counter) {
        return counter.missed() + counter.covered();
    }
}
