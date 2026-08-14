package com.architester.crap4j.core;

import java.util.List;
import java.util.Locale;

/** Computes startup warnings for coupled threshold settings. */
public final class ThresholdWarnings {
    private ThresholdWarnings() {}

    public static List<ThresholdWarning> compute(double threshold, int complexityCap) {
        long worstScore = (long) complexityCap * complexityCap + complexityCap;
        if (threshold >= worstScore) {
            String message = String.format(
                    Locale.ROOT,
                    """
                    WARNING: threshold %.1f is unreachable under complexity cap %d.
                    The worst score a cc %d method can reach is %d (fully uncovered).
                    The CRAP gate can never fire, this run is a plain complexity check.
                    Suggested pairing: threshold %.1f (threshold = cap demands full coverage at the cap).""",
                    threshold,
                    complexityCap,
                    complexityCap,
                    worstScore,
                    (double) complexityCap);
            return List.of(new ThresholdWarning(ThresholdWarning.Kind.UNREACHABLE_GATE, message));
        }
        if (threshold < complexityCap) {
            int hiddenCap = (int) Math.floor(threshold);
            String message = String.format(
                    Locale.ROOT,
                    """
                    WARNING: threshold %.1f is below complexity cap %d.
                    CRAP >= cc always, so every method over cc %d fails regardless of coverage.
                    The threshold is acting as a hidden complexity cap of %d.
                    Suggested pairing: complexity cap %d, or threshold %.1f.""",
                    threshold,
                    complexityCap,
                    hiddenCap,
                    hiddenCap,
                    hiddenCap,
                    (double) complexityCap);
            return List.of(new ThresholdWarning(ThresholdWarning.Kind.HIDDEN_COMPLEXITY_CAP, message));
        }
        return List.of();
    }
}
