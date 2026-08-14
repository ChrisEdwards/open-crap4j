package com.architester.crap4j.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Writes the deterministic JUnit XML sidecar used by CI report renderers. */
public final class JunitXmlReportWriter {
    public String write(
            GateResult result,
            GateConfig config,
            String producerName,
            ReportProducer producer) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(producerName, "producerName");
        Objects.requireNonNull(producer, "producer");
        if (result.requireTightBaseline() != config.requireTightBaseline()) {
            throw new IllegalArgumentException("Gate result and report config disagree on tight baseline mode");
        }

        List<Row> rows = rows(result);
        Map<String, Integer> nameCounts = new HashMap<>();
        for (Row row : rows) {
            nameCounts.merge(row.className() + '\0' + row.baseName(), 1, Integer::sum);
        }
        int failures = result.violations();
        int skipped = result.baselinedDebt()
                + (config.requireTightBaseline() ? 0 : result.slackEntries().size());

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<testsuites>\n")
                .append("  <testsuite name=\"").append(escaped("crap4j." + producerName))
                .append("\" tests=\"").append(rows.size())
                .append("\" failures=\"").append(failures)
                .append("\" errors=\"0\" skipped=\"").append(skipped)
                .append("\" time=\"0\">\n");
        for (Row row : rows) {
            appendRow(xml, row, nameCounts, config, producer, result);
        }
        return xml.append("  </testsuite>\n</testsuites>\n").toString();
    }

    private static List<Row> rows(GateResult result) {
        List<Row> rows = new ArrayList<>();
        ReportOrder.methods(result.methods()).stream()
                .map(Row::method)
                .forEach(rows::add);
        ReportOrder.slack(result.slackEntries()).stream()
                .map(Row::slack)
                .forEach(rows::add);
        return rows;
    }

    private static void appendRow(
            StringBuilder xml,
            Row row,
            Map<String, Integer> nameCounts,
            GateConfig config,
            ReportProducer producer,
            GateResult result) {
        String collisionKey = row.className() + '\0' + row.baseName();
        String displayName = row.prettyName();
        if (nameCounts.getOrDefault(collisionKey, 0) > 1) {
            displayName += " [" + row.key().descriptor() + "]";
        }
        if (row.slack() != null) {
            displayName += " [slack]";
        }
        xml.append("    <testcase classname=\"").append(escaped(row.className()))
                .append("\" name=\"").append(escaped(displayName)).append("\" time=\"0\"");

        String child = child(row, config, producer, result);
        if (child == null) {
            xml.append("/>\n");
        } else {
            xml.append(">\n      ").append(child).append("\n    </testcase>\n");
        }
    }

    private static String child(
            Row row, GateConfig config, ReportProducer producer, GateResult result) {
        if (row.assessment() != null) {
            MethodAssessment assessment = row.assessment();
            return switch (assessment.status()) {
                case OK -> null;
                case BASELINED -> "<skipped message=\""
                        + escaped(baselinedMessage(assessment)) + "\"/>";
                case VIOLATION -> "<failure type=\""
                        + escaped(primaryReason(assessment).serializedName())
                        + "\" message=\"" + escaped(violationMessage(assessment, config)) + "\"/>";
            };
        }

        SlackBaselineEntry slack = row.slack();
        String message = slackMessage(slack, result, config.requireTightBaseline(), producer);
        if (config.requireTightBaseline()) {
            return "<failure type=\"" + escaped(slack.reason().serializedName())
                    + "\" message=\"" + escaped(message) + "\"/>";
        }
        return "<skipped message=\"" + escaped(message) + "\"/>";
    }

    private static GateReason primaryReason(MethodAssessment assessment) {
        if (assessment.reasons().isEmpty()) {
            throw new IllegalArgumentException("Violation methods require a reason");
        }
        return assessment.reasons().get(0);
    }

    private static String violationMessage(MethodAssessment assessment, GateConfig config) {
        ScoredMethod method = assessment.method();
        String prefix = metrics(method);
        boolean regressed = assessment.reasons().contains(GateReason.CRAP_REGRESSED)
                || assessment.reasons().contains(GateReason.COMPLEXITY_REGRESSED);
        if (regressed) {
            return prefix + ", " + ReportMessages.regressionDetail(assessment, config.complexityCap());
        }

        StringBuilder message = new StringBuilder(prefix);
        if (assessment.reasons().contains(GateReason.CRAP_OVER_THRESHOLD)) {
            message.append(", over the CRAP threshold ")
                    .append(JsonText.policyDecimal(config.threshold()));
        }
        if (assessment.reasons().contains(GateReason.COMPLEXITY_OVER_CAP)) {
            message.append(", over the complexity cap ").append(config.complexityCap());
        }
        return message.toString();
    }

    private static String metrics(ScoredMethod method) {
        return ReportMessages.metrics(method);
    }

    private static String baselinedMessage(MethodAssessment assessment) {
        BaselineEntry allowance = assessment.allowance().orElseThrow();
        return "baselined debt, " + ReportMessages.allowance(allowance) + ", passing";
    }

    private static String slackMessage(
            SlackBaselineEntry slack,
            GateResult result,
            boolean tight,
            ReportProducer producer) {
        String detail = ReportMessages.slackDetail(slack, result);
        return "slack: " + slack.reason().serializedName() + ", " + detail
                + (tight ? ", tight baseline required" : "")
                + ", run " + producer.tightenCommand();
    }

    private static String escaped(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            switch (value.charAt(index)) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(value.charAt(index));
            }
        }
        return escaped.toString();
    }

    private record Row(
            MethodKey key,
            MethodAssessment assessment,
            SlackBaselineEntry slack) {
        static Row method(MethodAssessment assessment) {
            return new Row(MethodKey.of(assessment.method()), assessment, null);
        }

        static Row slack(SlackBaselineEntry slack) {
            return new Row(slack.key(), null, slack);
        }

        String className() {
            return key.className().replace('/', '.');
        }

        String prettyName() {
            return key.methodName() + "(" + JvmDescriptors.parameterList(key.descriptor()) + ")";
        }

        String baseName() {
            return prettyName() + (slack == null ? "" : " [slack]");
        }
    }
}
