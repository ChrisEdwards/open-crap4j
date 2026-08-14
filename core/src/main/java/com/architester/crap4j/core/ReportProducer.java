package com.architester.crap4j.core;

/** Surface that requested a report, used to name its available remedies. */
public enum ReportProducer {
    GRADLE("crapBaseline", "crapBaselineTighten"),
    CLI("baseline", "tighten");

    private final String baselineCommand;
    private final String tightenCommand;

    ReportProducer(String baselineCommand, String tightenCommand) {
        this.baselineCommand = baselineCommand;
        this.tightenCommand = tightenCommand;
    }

    public String baselineCommand() {
        return baselineCommand;
    }

    public String tightenCommand() {
        return tightenCommand;
    }
}
