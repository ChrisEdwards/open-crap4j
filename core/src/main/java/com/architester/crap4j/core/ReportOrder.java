package com.architester.crap4j.core;

import java.util.Comparator;
import java.util.List;

/** Canonical ordering shared by every report format. */
final class ReportOrder {
    private static final Comparator<MethodAssessment> METHODS =
            Comparator.comparingDouble((MethodAssessment assessment) -> assessment.method().crapScore())
                    .reversed()
                    .thenComparing(assessment -> MethodKey.of(assessment.method()));

    private ReportOrder() {}

    static List<MethodAssessment> methods(List<MethodAssessment> methods) {
        return methods.stream().sorted(METHODS).toList();
    }

    static List<SlackBaselineEntry> slack(List<SlackBaselineEntry> entries) {
        return entries.stream().sorted(Comparator.comparing(SlackBaselineEntry::key)).toList();
    }
}
