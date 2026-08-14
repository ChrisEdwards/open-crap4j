package com.architester.crap4j.core;

/** Why tightening would change a baseline entry. */
public enum SlackReason {
    METHOD_GONE("method-gone"),
    UNDER_LIMITS("under-limits"),
    EXCESS_ALLOWANCE("excess-allowance");

    private final String serializedName;

    SlackReason(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
