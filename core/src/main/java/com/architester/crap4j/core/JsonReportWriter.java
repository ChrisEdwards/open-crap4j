package com.architester.crap4j.core;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Renders the deterministic machine-readable report defined by format version 1. */
public final class JsonReportWriter {
    public static final int FORMAT_VERSION = 1;

    public String write(
            GateResult result,
            GateConfig config,
            String toolVersion,
            boolean advisory,
            Optional<String> baselineFile) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(toolVersion, "toolVersion");
        Objects.requireNonNull(baselineFile, "baselineFile");

        List<MethodAssessment> methods = result.methods().stream()
                .sorted(Comparator.comparingDouble(
                                (MethodAssessment assessment) -> assessment.method().crapScore())
                        .reversed()
                        .thenComparing(assessment -> MethodKey.of(assessment.method())))
                .toList();
        List<SlackBaselineEntry> slack = result.slackEntries().stream()
                .sorted(Comparator.comparing(SlackBaselineEntry::key))
                .toList();

        StringBuilder json = new StringBuilder("{\n")
                .append("  \"formatVersion\": ").append(FORMAT_VERSION).append(",\n")
                .append("  \"toolVersion\": ").append(JsonText.quoted(toolVersion)).append(",\n")
                .append("  \"status\": ").append(JsonText.quoted(status(result, advisory))).append(",\n")
                .append("  \"advisory\": ").append(advisory).append(",\n")
                .append("  \"mode\": ")
                .append(JsonText.quoted(config.changedFileMode() ? "changed-file" : "whole-repo"))
                .append(",\n")
                .append("  \"threshold\": ").append(JsonText.policyDecimal(config.threshold())).append(",\n")
                .append("  \"complexityCap\": ").append(config.complexityCap()).append(",\n")
                .append("  \"coverageSelection\": ")
                .append(JsonText.quoted(config.coverageSelection().serializedName())).append(",\n");
        baselineFile.ifPresent(path -> json.append("  \"baselineFile\": ")
                .append(JsonText.quoted(path)).append(",\n"));
        json.append("  \"summary\": {\n")
                .append("    \"methodsAnalyzed\": ").append(methods.size()).append(",\n")
                .append("    \"violations\": ").append(result.violations()).append(",\n")
                .append("    \"baselinedDebt\": ").append(result.baselinedDebt()).append(",\n")
                .append("    \"slackEntries\": ").append(slack.size()).append(",\n")
                .append("    \"excluded\": ").append(result.excluded()).append("\n")
                .append("  },\n")
                .append("  \"methods\": [");
        if (!methods.isEmpty()) {
            json.append('\n');
        }
        for (int index = 0; index < methods.size(); index++) {
            appendMethod(json, methods.get(index));
            json.append(index + 1 < methods.size() ? ",\n" : "\n");
        }
        json.append(methods.isEmpty() ? "],\n" : "  ],\n")
                .append("  \"slackBaselineEntries\": [");
        if (!slack.isEmpty()) {
            json.append('\n');
        }
        for (int index = 0; index < slack.size(); index++) {
            appendSlack(json, slack.get(index));
            json.append(index + 1 < slack.size() ? ",\n" : "\n");
        }
        return json.append(slack.isEmpty() ? "]\n}\n" : "  ]\n}\n").toString();
    }

    private static String status(GateResult result, boolean advisory) {
        if (result.violations() == 0) {
            return "pass";
        }
        return advisory ? "advisory" : "fail";
    }

    private static void appendMethod(StringBuilder json, MethodAssessment assessment) {
        ScoredMethod method = assessment.method();
        json.append("    {\n")
                .append("      \"class\": ").append(JsonText.quoted(method.className())).append(",\n")
                .append("      \"method\": ").append(JsonText.quoted(method.methodName())).append(",\n")
                .append("      \"descriptor\": ").append(JsonText.quoted(method.descriptor())).append(",\n");
        method.sourceFile().ifPresent(value -> json.append("      \"sourceFile\": ")
                .append(JsonText.quoted(value)).append(",\n"));
        method.line().ifPresent(value -> json.append("      \"line\": ").append(value).append(",\n"));
        json.append("      \"complexity\": ").append(method.complexity()).append(",\n")
                .append("      \"coverage\": ").append(JsonText.decimal(method.coverage(), 4)).append(",\n")
                .append("      \"coverageKind\": ")
                .append(JsonText.quoted(method.coverageKind().name().toLowerCase(Locale.ROOT))).append(",\n")
                .append("      \"crap\": ").append(JsonText.decimal(method.crapScore(), 2)).append(",\n")
                .append("      \"status\": ")
                .append(JsonText.quoted(assessment.status().name().toLowerCase(Locale.ROOT)));
        if (assessment.status() != MethodStatus.OK) {
            json.append(",\n      \"reasons\": [");
            for (int index = 0; index < assessment.reasons().size(); index++) {
                if (index > 0) {
                    json.append(", ");
                }
                json.append(JsonText.quoted(assessment.reasons().get(index).serializedName()));
            }
            json.append(']');
        }
        if (assessment.status() == MethodStatus.BASELINED) {
            BaselineEntry allowance = assessment.allowance().orElseThrow(
                    () -> new IllegalArgumentException("Baselined methods require an allowance"));
            json.append(",\n      \"baseline\": { \"crap\": ")
                    .append(JsonText.decimal(allowance.crapScore(), 2))
                    .append(", \"complexity\": ").append(allowance.complexity()).append(" }");
        }
        json.append("\n    }");
    }

    private static void appendSlack(StringBuilder json, SlackBaselineEntry entry) {
        json.append("    {\n")
                .append("      \"class\": ").append(JsonText.quoted(entry.key().className())).append(",\n")
                .append("      \"method\": ").append(JsonText.quoted(entry.key().methodName())).append(",\n")
                .append("      \"descriptor\": ").append(JsonText.quoted(entry.key().descriptor())).append(",\n")
                .append("      \"reason\": ").append(JsonText.quoted(entry.reason().serializedName())).append("\n")
                .append("    }");
    }

}
