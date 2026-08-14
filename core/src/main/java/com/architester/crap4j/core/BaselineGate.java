package com.architester.crap4j.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Applies the current CRAP threshold, complexity cap, and stored baseline allowances. */
public final class BaselineGate {
    public static final int FORMAT_VERSION = 1;
    public static final double EPSILON = 0.05;

    public GateResult evaluate(
            ScoringResult scoring, Optional<Baseline> baseline, GateConfig config) {
        List<ConfigWarning> configWarnings = new ArrayList<>();
        Map<MethodKey, BaselineEntry> allowances = baseline
                .map(value -> validate(value, config, configWarnings))
                .orElseGet(Map::of);

        List<MethodAssessment> methods = scoring.methods().stream()
                .map(method -> assess(method, allowances.get(MethodKey.of(method)), config))
                .toList();
        List<SlackBaselineEntry> slack = config.changedFileMode() || baseline.isEmpty()
                ? List.of()
                : detectSlack(baseline.orElseThrow(), scoring, config);
        return new GateResult(
                methods,
                scoring.excluded(),
                slack,
                configWarnings,
                config.requireTightBaseline());
    }

    private static Map<MethodKey, BaselineEntry> validate(
            Baseline baseline, GateConfig config, List<ConfigWarning> warnings) {
        if (baseline.formatVersion() != FORMAT_VERSION) {
            throw mismatch("formatVersion");
        }
        if (baseline.coverageSelection() != config.coverageSelection()) {
            throw mismatch("coverageSelection");
        }
        if (Double.compare(baseline.threshold(), config.threshold()) != 0) {
            warnings.add(ConfigWarning.THRESHOLD_MISMATCH);
        }
        if (baseline.complexityCap() != config.complexityCap()) {
            warnings.add(ConfigWarning.COMPLEXITY_CAP_MISMATCH);
        }
        Map<MethodKey, BaselineEntry> entries = new HashMap<>();
        for (BaselineEntry entry : baseline.entries()) {
            if (entries.put(entry.key(), entry) != null) {
                throw new BaselineMismatchException(
                        "Baseline contains duplicate method " + entry.key() + "; re-baseline is required");
            }
        }
        return entries;
    }

    private static BaselineMismatchException mismatch(String field) {
        return new BaselineMismatchException(
                "Baseline " + field + " differs from the current analysis; re-baseline is required");
    }

    private static MethodAssessment assess(
            ScoredMethod method, BaselineEntry allowance, GateConfig config) {
        boolean crapOver = method.crapScore() > config.threshold();
        boolean complexityOver = method.complexity() > config.complexityCap();
        Optional<BaselineEntry> stored = Optional.ofNullable(allowance);
        if (!crapOver && !complexityOver) {
            return new MethodAssessment(method, MethodStatus.OK, List.of(), stored);
        }
        if (allowance == null) {
            return new MethodAssessment(
                    method,
                    MethodStatus.VIOLATION,
                    thresholdAndCapReasons(crapOver, complexityOver),
                    Optional.empty());
        }

        List<GateReason> regressions = new ArrayList<>();
        if (method.crapScore() > allowance.crapScore() + EPSILON) {
            regressions.add(GateReason.CRAP_REGRESSED);
        }
        if (method.complexity() > allowance.complexity()) {
            regressions.add(GateReason.COMPLEXITY_REGRESSED);
        }
        if (!regressions.isEmpty()) {
            return new MethodAssessment(method, MethodStatus.VIOLATION, regressions, stored);
        }
        return new MethodAssessment(
                method,
                MethodStatus.BASELINED,
                thresholdAndCapReasons(crapOver, complexityOver),
                stored);
    }

    private static List<GateReason> thresholdAndCapReasons(
            boolean crapOver, boolean complexityOver) {
        List<GateReason> reasons = new ArrayList<>();
        if (crapOver) {
            reasons.add(GateReason.CRAP_OVER_THRESHOLD);
        }
        if (complexityOver) {
            reasons.add(GateReason.COMPLEXITY_OVER_CAP);
        }
        return reasons;
    }

    private static List<SlackBaselineEntry> detectSlack(
            Baseline baseline, ScoringResult scoring, GateConfig config) {
        Map<MethodKey, ScoredMethod> current = scoring.methodsByKey();
        List<SlackBaselineEntry> slack = new ArrayList<>();
        for (BaselineEntry entry : baseline.entries()) {
            ScoredMethod method = current.get(entry.key());
            if (method == null) {
                slack.add(new SlackBaselineEntry(entry.key(), SlackReason.METHOD_GONE));
            } else if (method.crapScore() <= config.threshold()
                    && method.complexity() <= config.complexityCap()) {
                slack.add(new SlackBaselineEntry(entry.key(), SlackReason.UNDER_LIMITS));
            } else if (entry.crapScore() > method.crapScore() + EPSILON
                    || entry.complexity() > method.complexity()) {
                slack.add(new SlackBaselineEntry(entry.key(), SlackReason.EXCESS_ALLOWANCE));
            }
        }
        return slack;
    }
}
