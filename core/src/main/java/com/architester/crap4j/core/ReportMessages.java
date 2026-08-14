package com.architester.crap4j.core;

import java.util.Locale;

/** Shared human vocabulary used by text and JUnit display reports. */
final class ReportMessages {
    private ReportMessages() {}

    static String metrics(ScoredMethod method) {
        String kind = method.coverageKind() == CoverageKind.BRANCH ? "branch" : "instr";
        return String.format(Locale.ROOT, "CRAP %.2f, cc %d, %.1f%% %s",
                method.crapScore(), method.complexity(), method.coverage() * 100, kind);
    }

    static String allowance(BaselineEntry allowance) {
        return "allowance crap " + JsonText.decimal(allowance.crapScore(), 2)
                + " cc " + allowance.complexity();
    }

    static String regressionDetail(MethodAssessment assessment, int complexityCap) {
        BaselineEntry allowance = assessment.allowance().orElseThrow();
        ScoredMethod method = assessment.method();
        StringBuilder detail = new StringBuilder("baselined at crap ")
                .append(JsonText.decimal(allowance.crapScore(), 2))
                .append(" cc ").append(allowance.complexity()).append(", regressed:");
        if (assessment.reasons().contains(GateReason.CRAP_REGRESSED)) {
            detail.append(" crap +")
                    .append(JsonText.decimal(method.crapScore() - allowance.crapScore(), 2));
        }
        if (assessment.reasons().contains(GateReason.COMPLEXITY_REGRESSED)) {
            if (assessment.reasons().contains(GateReason.CRAP_REGRESSED)) {
                detail.append(',');
            }
            detail.append(" complexity +").append(method.complexity() - allowance.complexity());
        }
        if (method.complexity() > complexityCap) {
            detail.append(", over the cap");
        }
        return detail.toString();
    }

    static String slackDetail(SlackBaselineEntry slack, GateResult result) {
        return switch (slack.reason()) {
            case METHOD_GONE -> "no such method in the report";
            case UNDER_LIMITS -> "now passes on its own";
            case EXCESS_ALLOWANCE -> {
                MethodAssessment assessment = result.methods().stream()
                        .filter(method -> MethodKey.of(method.method()).equals(slack.key()))
                        .findFirst()
                        .orElseThrow();
                BaselineEntry allowance = assessment.allowance().orElseThrow();
                yield "allowance " + JsonText.decimal(allowance.crapScore(), 2)
                        + ", scores " + JsonText.decimal(assessment.method().crapScore(), 2)
                        + " today";
            }
        };
    }
}
