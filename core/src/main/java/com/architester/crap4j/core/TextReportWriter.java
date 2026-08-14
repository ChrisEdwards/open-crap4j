package com.architester.crap4j.core;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Renders the human-facing report body defined by the locked text scenarios. */
public final class TextReportWriter {
    private static final String METHOD_HEADER = "   CRAP   cc  coverage        method";
    private static final String ALLOWANCE_HEADER =
            "   CRAP   cc  coverage        method                                              allowance";

    public String write(
            GateResult result,
            GateConfig config,
            boolean advisory,
            Optional<String> baselineFile,
            ReportProducer producer,
            OptionalInt showPassing) {
        return render(result, config, advisory, baselineFile, producer, showPassing).combined();
    }

    public TextReportOutput render(
            GateResult result,
            GateConfig config,
            boolean advisory,
            Optional<String> baselineFile,
            ReportProducer producer,
            OptionalInt showPassing) {
        return render(result, config, advisory, baselineFile, producer, showPassing, Optional.empty());
    }

    public TextReportOutput render(
            GateResult result,
            GateConfig config,
            boolean advisory,
            Optional<String> baselineFile,
            ReportProducer producer,
            OptionalInt showPassing,
            Optional<String> reportName) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(baselineFile, "baselineFile");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(showPassing, "showPassing");
        Objects.requireNonNull(reportName, "reportName");
        if (showPassing.isPresent() && showPassing.getAsInt() < 0) {
            throw new IllegalArgumentException("showPassing must be non-negative");
        }

        StringBuilder report = new StringBuilder(heading(reportName));
        report.append(echo(config, advisory, baselineFile, reportName.isEmpty())).append("\n\n");
        StringBuilder diagnostics = new StringBuilder();
        List<ThresholdWarning> warnings =
                ThresholdWarnings.compute(config.threshold(), config.complexityCap());
        for (int index = 0; index < warnings.size(); index++) {
            diagnostics.append(warnings.get(index).message()).append('\n');
            if (index + 1 < warnings.size()) {
                diagnostics.append('\n');
            }
        }
        if (!warnings.isEmpty()) {
            if (result.methods().isEmpty() && result.excluded() == 0) {
                report.setLength(report.length() - 1);
                return new TextReportOutput(report.toString(), diagnostics.toString());
            }
        }

        List<MethodAssessment> violations = methods(result, MethodStatus.VIOLATION);
        List<MethodAssessment> baselined = methods(result, MethodStatus.BASELINED);
        if (!violations.isEmpty()) {
            appendViolations(report, violations, advisory, config.complexityCap());
        }
        if (!baselined.isEmpty()) {
            appendBaselined(report, baselined);
        }
        if (!result.slackEntries().isEmpty()) {
            appendSlack(report, result, config.requireTightBaseline());
        }
        if (showPassing.isPresent() && showPassing.getAsInt() > 0) {
            appendPassing(report, result, showPassing.getAsInt());
        }

        appendSummary(report, result, baselineFile.isPresent());
        appendStatus(report, result, advisory, producer);
        return new TextReportOutput(report.toString(), diagnostics.toString());
    }

    private static String heading(Optional<String> reportName) {
        return reportName.filter(name -> !name.isBlank())
                .map(name -> "open-crap4j - Report for module: " + name.strip() + "\n")
                .orElse("");
    }

    private static String echo(
            GateConfig config,
            boolean advisory,
            Optional<String> baselineFile,
            boolean includeToolName) {
        StringBuilder echo = new StringBuilder(includeToolName ? "open-crap4j  threshold " : "threshold ")
                .append(JsonText.policyDecimal(config.threshold()))
                .append("  complexity cap ").append(config.complexityCap())
                .append("  coverage ").append(config.coverageSelection().serializedName())
                .append(baselineFile.map(path -> "  baseline " + path).orElse("  no baseline"));
        if (config.requireTightBaseline()) {
            echo.append("  tight baseline required");
        }
        if (advisory) {
            echo.append("  ADVISORY");
        }
        return echo.toString();
    }

    private static List<MethodAssessment> methods(GateResult result, MethodStatus status) {
        return ReportOrder.methods(result.methods()).stream()
                .filter(method -> method.status() == status)
                .toList();
    }

    private static void appendViolations(
            StringBuilder report,
            List<MethodAssessment> violations,
            boolean advisory,
            int complexityCap) {
        report.append("Violations (").append(violations.size()).append(") — ")
                .append(advisory
                        ? "reported only, advisory mode passes the build"
                        : "over the limits, not excused, these fail the build")
                .append("\n\n").append(METHOD_HEADER).append('\n');
        for (MethodAssessment assessment : violations) {
            report.append(methodPrefix(assessment.method()))
                    .append(String.format(Locale.ROOT, "%-48s   %s%n",
                            displayName(assessment.method()), location(assessment.method())));
            if (hasRegression(assessment)) {
                appendRegressionDetail(report, assessment, complexityCap);
            }
        }
        report.append('\n');
    }

    private static void appendBaselined(
            StringBuilder report, List<MethodAssessment> baselined) {
        report.append("Baselined debt (").append(baselined.size())
                .append(") — over the limits but excused by the baseline, passing\n\n")
                .append(ALLOWANCE_HEADER).append('\n');
        for (MethodAssessment assessment : baselined) {
            BaselineEntry allowance = assessment.allowance().orElseThrow();
            report.append(methodPrefix(assessment.method()))
                    .append(String.format(Locale.ROOT, "%-48s    crap %.2f  cc %d%n",
                            displayName(assessment.method()), allowance.crapScore(), allowance.complexity()));
        }
        report.append('\n');
    }

    private static void appendSlack(
            StringBuilder report, GateResult result, boolean tight) {
        report.append("Slack in the baseline (").append(result.slackEntries().size()).append(") — ")
                .append(tight
                        ? "tight baseline required, these fail the build"
                        : "more allowance than needed, informational")
                .append("\n\n");
        List<SlackBaselineEntry> entries = ReportOrder.slack(result.slackEntries());
        for (SlackBaselineEntry entry : entries) {
            report.append(String.format(Locale.ROOT, "  %-18s%-48s   %s%n",
                    entry.reason().serializedName(), displayName(entry.key()),
                    slackDescription(entry, result)));
        }
        report.append('\n');
    }

    private static String slackDescription(SlackBaselineEntry entry, GateResult result) {
        return ReportMessages.slackDetail(entry, result);
    }

    private static void appendPassing(StringBuilder report, GateResult result, int limit) {
        List<MethodAssessment> passing = methods(result, MethodStatus.OK).stream()
                .limit(limit)
                .toList();
        if (passing.isEmpty()) {
            return;
        }
        report.append("Passing methods (").append(passing.size())
                .append(") — highest CRAP scores still within the limits\n\n")
                .append(METHOD_HEADER).append('\n');
        for (MethodAssessment assessment : passing) {
            report.append(methodPrefix(assessment.method()))
                    .append(displayName(assessment.method())).append('\n');
        }
        report.append('\n');
    }

    private static void appendSummary(
            StringBuilder report, GateResult result, boolean hasBaseline) {
        report.append(result.methods().size()).append(" methods analyzed, ")
                .append(result.excluded()).append(" excluded\n");
        if (!hasBaseline) {
            report.append(plural(result.violations(), "violation"))
                    .append(", ").append(result.baselinedDebt())
                    .append(" baselined, no baseline\n\n");
            return;
        }
        if (result.requireTightBaseline() && !result.slackEntries().isEmpty()) {
            report.append(result.violations()).append(" violations (")
                    .append(result.slackEntries().size()).append(" slack)");
        } else {
            report.append(plural(result.violations(), "violation"));
        }
        report.append(", ").append(result.baselinedDebt()).append(" baselined, ")
                .append(result.slackEntries().size()).append(" slack entries\n\n");
    }

    private static void appendStatus(
            StringBuilder report,
            GateResult result,
            boolean advisory,
            ReportProducer producer) {
        if (result.violations() == 0) {
            if (!result.slackEntries().isEmpty()) {
                report.append("PASS. The baseline has slack, lock in the progress with ")
                        .append(producer.tightenCommand()).append(".\n");
            } else if (result.baselinedDebt() > 0) {
                report.append("PASS with ").append(result.baselinedDebt())
                        .append(" baselined methods carrying known debt.\n")
                        .append("These fail the build only if they grow past their stored allowance.\n");
            } else {
                report.append("PASS\n");
            }
            return;
        }
        if (advisory) {
            report.append("ADVISORY: ").append(result.violations())
                    .append(" violations reported, build allowed to pass.\n");
            return;
        }
        if (result.requireTightBaseline() && !result.slackEntries().isEmpty()
                && result.methodViolations() == 0) {
            report.append("FAIL: baseline is not tight, ")
                    .append(result.slackEntries().size()).append(result.slackEntries().size() == 1
                            ? " slack entry"
                            : " slack entries")
                    .append(". Run ").append(producer.tightenCommand()).append(".\n");
            return;
        }
        long regressions = result.methods().stream()
                .filter(TextReportWriter::hasRegression)
                .count();
        if (regressions > 0) {
            report.append("FAIL: ").append(regressions)
                    .append(regressions == 1 ? " baselined method regressed" : " baselined methods regressed")
                    .append(" past its allowance.\n")
                    .append("Fix with tests, or re-admit the new debt with ")
                    .append(producer.baselineCommand())
                    .append(" (reviewed, it raises the allowance).\n");
            return;
        }
        report.append("FAIL: ").append(result.methodViolations())
                .append(result.methodViolations() == 1
                        ? " method over the CRAP threshold.\n"
                        : " methods over the CRAP threshold.\n")
                .append("Fix with tests, or admit them as known debt with ")
                .append(producer.baselineCommand()).append(".\n");
    }

    private static String methodPrefix(ScoredMethod method) {
        String kind = method.coverageKind() == CoverageKind.BRANCH ? "branch" : "instr";
        return String.format(Locale.ROOT, "  %5.2f  %3d  %4.1f%% %-6s    ",
                method.crapScore(), method.complexity(), method.coverage() * 100, kind);
    }

    private static String displayName(ScoredMethod method) {
        return shortClassName(method.className()) + "." + method.methodName();
    }

    private static String displayName(MethodKey key) {
        return shortClassName(key.className()) + "." + key.methodName();
    }

    private static String shortClassName(String className) {
        int separator = className.lastIndexOf('/');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static String location(ScoredMethod method) {
        if (method.sourceFile().isEmpty() || method.line().isEmpty()) {
            return "";
        }
        return "(" + method.sourceFile().orElseThrow() + ":" + method.line().orElseThrow() + ")";
    }

    private static boolean hasRegression(MethodAssessment assessment) {
        return assessment.reasons().contains(GateReason.CRAP_REGRESSED)
                || assessment.reasons().contains(GateReason.COMPLEXITY_REGRESSED);
    }

    private static void appendRegressionDetail(
            StringBuilder report, MethodAssessment assessment, int complexityCap) {
        report.append("                              ")
                .append(ReportMessages.regressionDetail(assessment, complexityCap))
                .append('\n');
    }

    private static String plural(int count, String singular) {
        return count + " " + singular + (count == 1 ? "" : "s");
    }
}
