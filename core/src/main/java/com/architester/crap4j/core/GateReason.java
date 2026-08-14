package com.architester.crap4j.core;

/** Machine-readable reason attached to a non-OK method. */
public enum GateReason {
    CRAP_OVER_THRESHOLD("crap-over-threshold"),
    COMPLEXITY_OVER_CAP("complexity-over-cap"),
    CRAP_REGRESSED("crap-regressed"),
    COMPLEXITY_REGRESSED("complexity-regressed");

    private final String serializedName;

    GateReason(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
