package com.architester.crap4j.core;

/** Policy used to select the JaCoCo coverage counter. */
public enum CoverageSelection {
    BRANCH_PREFERRED("branch-preferred");

    private final String serializedName;

    CoverageSelection(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CoverageSelection fromSerializedName(String value) {
        for (CoverageSelection selection : values()) {
            if (selection.serializedName.equals(value)) {
                return selection;
            }
        }
        throw new IllegalArgumentException("Unsupported coverageSelection: " + value);
    }
}
