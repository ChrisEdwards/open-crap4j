package com.architester.crap4j.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Creates a new baseline or safely tightens an existing one. */
public final class BaselineOperations {
    public Baseline rebaseline(
            ScoringResult scoring,
            GateConfig config,
            String toolVersion,
            String generated) {
        requireWholeRepo(config);
        List<BaselineEntry> entries = scoring.methods().stream()
                .filter(method -> method.crapScore() > config.threshold()
                        || method.complexity() > config.complexityCap())
                .map(method -> new BaselineEntry(
                        MethodKey.of(method), twoDecimals(method.crapScore()), method.complexity()))
                .toList();
        return new Baseline(
                BaselineGate.FORMAT_VERSION,
                toolVersion,
                generated,
                config.coverageSelection(),
                config.threshold(),
                config.complexityCap(),
                entries);
    }

    public Baseline tighten(
            Baseline baseline,
            ScoringResult scoring,
            GateConfig config,
            String toolVersion,
            String generated) {
        requireWholeRepo(config);
        GateResult result = new BaselineGate().evaluate(scoring, Optional.of(baseline), config);
        boolean policyChanged = Double.compare(baseline.threshold(), config.threshold()) != 0
                || baseline.complexityCap() != config.complexityCap();
        if (result.slackEntries().isEmpty() && !policyChanged) {
            return baseline;
        }

        Map<MethodKey, ScoredMethod> current = scoring.methodsByKey();
        Map<MethodKey, SlackReason> slack = result.slackEntries().stream()
                .collect(Collectors.toUnmodifiableMap(
                        SlackBaselineEntry::key, SlackBaselineEntry::reason));

        List<BaselineEntry> tightened = new ArrayList<>();
        for (BaselineEntry entry : baseline.entries()) {
            SlackReason reason = slack.get(entry.key());
            if (reason == SlackReason.METHOD_GONE || reason == SlackReason.UNDER_LIMITS) {
                continue;
            }
            if (reason == SlackReason.EXCESS_ALLOWANCE) {
                ScoredMethod method = current.get(entry.key());
                double crapScore = entry.crapScore() > method.crapScore() + BaselineGate.EPSILON
                        ? twoDecimals(method.crapScore())
                        : entry.crapScore();
                int complexity = Math.min(entry.complexity(), method.complexity());
                tightened.add(new BaselineEntry(entry.key(), crapScore, complexity));
            } else {
                tightened.add(entry);
            }
        }
        return new Baseline(
                baseline.formatVersion(),
                toolVersion,
                generated,
                baseline.coverageSelection(),
                config.threshold(),
                config.complexityCap(),
                tightened);
    }

    private static void requireWholeRepo(GateConfig config) {
        if (config.changedFileMode()) {
            throw new IllegalArgumentException("Baseline writes require whole-repo mode");
        }
    }

    private static double twoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
