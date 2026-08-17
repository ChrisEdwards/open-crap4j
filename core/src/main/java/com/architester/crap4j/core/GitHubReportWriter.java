package com.architester.crap4j.core;

import java.util.Comparator;
import java.util.Locale;

/** Renders GitHub Actions job summaries and source annotations. */
public final class GitHubReportWriter {
    private static final int TOP_METHODS = 20;

    public String summary(GateResult result, GateConfig config, String reportName) {
        long passing = result.methods().stream()
                .filter(method -> method.status() == MethodStatus.OK)
                .count();
        int slackViolations = result.requireTightBaseline() ? result.slackEntries().size() : 0;
        StringBuilder markdown = new StringBuilder("## CRAP report: ")
                .append(markdown(reportName)).append("\n\n")
                .append("**").append(result.violations()).append(" violations");
        if (slackViolations > 0) {
            markdown.append(" (").append(slackViolations).append(" slack)");
        }
        markdown.append(" · ")
                .append(result.baselinedDebt()).append(" baselined · ")
                .append(passing).append(" passing**\n\n")
                .append("Threshold **").append(JsonText.policyDecimal(config.threshold()))
                .append("** · Complexity cap **").append(config.complexityCap()).append("**\n\n")
                .append("### Highest CRAP scores\n\n")
                .append("| CRAP | cc | Coverage | Status | Method |\n")
                .append("| ---: | --: | ---: | :--- | :--- |\n");
        result.methods().stream()
                .sorted(Comparator.comparingDouble(
                        (MethodAssessment assessment) -> assessment.method().crapScore()).reversed())
                .limit(TOP_METHODS)
                .forEach(assessment -> appendMethod(markdown, assessment));
        return markdown.append('\n').toString();
    }

    public String annotations(GateResult result, GateConfig config, String sourceRoot) {
        StringBuilder annotations = new StringBuilder();
        result.methods().stream()
                .filter(assessment -> assessment.status() == MethodStatus.VIOLATION)
                .filter(assessment -> assessment.method().sourceFile().isPresent())
                .forEach(assessment -> appendAnnotation(annotations, assessment, config, sourceRoot));
        return annotations.toString();
    }

    private static void appendMethod(StringBuilder markdown, MethodAssessment assessment) {
        ScoredMethod method = assessment.method();
        markdown.append("| ").append(String.format(Locale.ROOT, "%.2f", method.crapScore()))
                .append(" | ").append(method.complexity())
                .append(" | ").append(String.format(Locale.ROOT, "%.1f%% %s",
                        method.coverage() * 100, method.coverageKind().name().toLowerCase(Locale.ROOT)))
                .append(" | ").append(status(assessment.status()))
                .append(" | `").append(markdown(method.className().replace('/', '.')))
                .append('.').append(markdown(method.methodName())).append("` |\n");
    }

    private static void appendAnnotation(
            StringBuilder output,
            MethodAssessment assessment,
            GateConfig config,
            String sourceRoot) {
        ScoredMethod method = assessment.method();
        String path = sourcePath(sourceRoot, method);
        output.append("::error file=").append(commandProperty(path));
        method.line().ifPresent(line -> output.append(",line=").append(line));
        output.append(",title=").append(commandProperty(String.format(
                        Locale.ROOT, "CRAP %.2f in %s", method.crapScore(), method.methodName())))
                .append("::").append(commandMessage(String.format(
                        Locale.ROOT,
                        "CRAP %.2f exceeds threshold %.1f; complexity %d, coverage %.1f%% %s",
                        method.crapScore(), config.threshold(), method.complexity(),
                        method.coverage() * 100,
                        method.coverageKind().name().toLowerCase(Locale.ROOT))))
                .append('\n');
    }

    private static String sourcePath(String sourceRoot, ScoredMethod method) {
        String className = method.className().replace('\\', '/');
        int separator = className.lastIndexOf('/');
        String packagePath = separator < 0 ? "" : className.substring(0, separator + 1);
        String prefix = sourceRoot.replace('\\', '/').replaceAll("/+$", "");
        return (prefix.isEmpty() ? "" : prefix + "/")
                + packagePath + method.sourceFile().orElseThrow();
    }

    private static String status(MethodStatus status) {
        return switch (status) {
            case VIOLATION -> "❌ violation";
            case BASELINED -> "⚠️ baselined";
            case OK -> "✅ passing";
        };
    }

    private static String markdown(String value) {
        return value.replace("|", "\\|").replace("`", "\\`");
    }

    private static String commandProperty(String value) {
        return commandMessage(value).replace(":", "%3A").replace(",", "%2C");
    }

    private static String commandMessage(String value) {
        return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }
}
