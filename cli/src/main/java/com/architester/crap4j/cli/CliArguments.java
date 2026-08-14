package com.architester.crap4j.cli;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

record CliArguments(
        Verb verb,
        String report,
        double threshold,
        int complexityCap,
        List<String> exclusions,
        List<String> classExclusions,
        boolean useDefaultExclusions,
        Optional<String> changedFiles,
        Optional<String> baseline,
        boolean requireTightBaseline,
        boolean advisory,
        OptionalInt showPassing,
        Optional<String> reportName,
        Optional<String> jsonReport,
        Optional<String> junitReport) {
    enum Verb {
        CHECK(Capability.GATES, Capability.CHANGED_FILES, Capability.ADVISORY,
                Capability.REQUIRE_TIGHT_BASELINE, Capability.REPORT_OUTPUTS),
        REPORT(Capability.INHERENTLY_ADVISORY, Capability.CHANGED_FILES,
                Capability.REQUIRE_TIGHT_BASELINE, Capability.REPORT_OUTPUTS),
        BASELINE(Capability.WRITES_BASELINE),
        TIGHTEN(Capability.TIGHTENS_BASELINE);

        private final Set<Capability> capabilities;

        Verb(Capability first, Capability... rest) {
            capabilities = EnumSet.of(first, rest);
        }

        static Verb parse(String value) {
            try {
                return valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new UsageException("Unknown verb: " + value);
            }
        }

        String commandName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        boolean gates() {
            return has(Capability.GATES);
        }

        boolean inherentlyAdvisory() {
            return has(Capability.INHERENTLY_ADVISORY);
        }

        boolean acceptsChangedFiles() {
            return has(Capability.CHANGED_FILES);
        }

        boolean acceptsAdvisory() {
            return has(Capability.ADVISORY);
        }

        boolean acceptsReportOutputs() {
            return has(Capability.REPORT_OUTPUTS);
        }

        boolean acceptsRequireTightBaseline() {
            return has(Capability.REQUIRE_TIGHT_BASELINE);
        }

        boolean writesBaseline() {
            return has(Capability.WRITES_BASELINE);
        }

        boolean tightensBaseline() {
            return has(Capability.TIGHTENS_BASELINE);
        }

        boolean requiresExistingExplicitBaseline() {
            return !writesBaseline();
        }

        private boolean has(Capability capability) {
            return capabilities.contains(capability);
        }

        private enum Capability {
            GATES,
            INHERENTLY_ADVISORY,
            CHANGED_FILES,
            ADVISORY,
            REQUIRE_TIGHT_BASELINE,
            REPORT_OUTPUTS,
            WRITES_BASELINE,
            TIGHTENS_BASELINE
        }
    }

    static CliArguments parse(String[] args) {
        Verb verb = Verb.parse(args[0]);
        String report = null;
        double threshold = 15.0;
        int complexityCap = 15;
        List<String> exclusions = new ArrayList<>();
        List<String> classExclusions = new ArrayList<>();
        boolean defaults = true;
        String changedFiles = null;
        String baseline = null;
        boolean tight = false;
        boolean advisory = false;
        OptionalInt showPassing = OptionalInt.empty();
        String reportName = null;
        String jsonReport = null;
        String junitReport = null;
        Set<String> seen = new HashSet<>();

        for (int index = 1; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) {
                throw new UsageException("Unexpected argument: " + argument);
            }
            int equals = argument.indexOf('=');
            String flag = equals < 0 ? argument : argument.substring(0, equals);
            String inlineValue = equals < 0 ? null : argument.substring(equals + 1);
            if (flag.equals("--exclude") || flag.equals("--exclude-class")) {
                Value value = value(args, index, flag, inlineValue);
                index = value.lastIndex();
                (flag.equals("--exclude") ? exclusions : classExclusions).add(value.text());
                continue;
            }
            if (!seen.add(flag)) {
                throw new UsageException("Duplicate flag: " + flag);
            }
            if (flag.equals("--require-tight-baseline") || flag.equals("--advisory")) {
                if (inlineValue != null) {
                    throw new UsageException(flag + " does not take a value");
                }
                if (flag.equals("--require-tight-baseline")) {
                    tight = true;
                } else {
                    advisory = true;
                }
                continue;
            }

            Value value = value(args, index, flag, inlineValue);
            index = value.lastIndex();
            try {
                switch (flag) {
                    case "--report" -> report = value.text();
                    case "--threshold" -> threshold = Double.parseDouble(value.text());
                    case "--complexity-cap" -> complexityCap = Integer.parseInt(value.text());
                    case "--use-default-exclusions" -> defaults = parseBoolean(flag, value.text());
                    case "--changed-files" -> changedFiles = value.text();
                    case "--baseline" -> baseline = value.text();
                    case "--show-passing" -> showPassing = showPassing(value.text());
                    case "--report-name" -> reportName = value.text();
                    case "--json-report" -> jsonReport = value.text();
                    case "--junit-report" -> junitReport = value.text();
                    default -> throw new UsageException("Unknown flag: " + flag);
                }
            } catch (NumberFormatException exception) {
                throw new UsageException("Invalid value for " + flag + ": " + value.text());
            }
        }

        if (report == null) {
            throw new UsageException("--report is required");
        }
        validateVerbFlags(verb, changedFiles, tight, advisory, showPassing,
                reportName, jsonReport, junitReport);
        return new CliArguments(
                verb, report, threshold, complexityCap, List.copyOf(exclusions),
                List.copyOf(classExclusions), defaults, Optional.ofNullable(changedFiles),
                Optional.ofNullable(baseline), tight, advisory, showPassing,
                Optional.ofNullable(reportName),
                Optional.ofNullable(jsonReport), Optional.ofNullable(junitReport));
    }

    private static void validateVerbFlags(
            Verb verb,
            String changedFiles,
            boolean tight,
            boolean advisory,
            OptionalInt showPassing,
            String reportName,
            String jsonReport,
            String junitReport) {
        if (!verb.acceptsChangedFiles() && changedFiles != null) {
            throw new UsageException("--changed-files is not allowed on " + verb.commandName());
        }
        if (changedFiles != null && tight) {
            throw new UsageException("--require-tight-baseline cannot be combined with --changed-files");
        }
        if (!verb.acceptsAdvisory() && advisory) {
            throw new UsageException("--advisory is only allowed on check");
        }
        if (!verb.acceptsRequireTightBaseline() && tight) {
            throw new UsageException("--require-tight-baseline is not allowed on " + verb.commandName());
        }
        validateReportOutputFlags(verb, showPassing, reportName, jsonReport, junitReport);
    }

    private static OptionalInt showPassing(String value) {
        int count = Integer.parseInt(value);
        if (count < 0) {
            throw new UsageException("--show-passing must be non-negative");
        }
        return OptionalInt.of(count);
    }

    private static void validateReportOutputFlags(
            Verb verb,
            OptionalInt showPassing,
            String reportName,
            String jsonReport,
            String junitReport) {
        if (!verb.acceptsReportOutputs() && (showPassing.isPresent()
                || reportName != null || jsonReport != null || junitReport != null)) {
            throw new UsageException("Report output flags are not allowed on " + verb.commandName());
        }
    }

    private static boolean parseBoolean(String flag, String value) {
        if (value.equals("true")) {
            return true;
        }
        if (value.equals("false")) {
            return false;
        }
        throw new UsageException("Invalid value for " + flag + ": " + value);
    }

    private static Value value(String[] args, int index, String flag, String inlineValue) {
        if (inlineValue != null) {
            if (inlineValue.isEmpty()) {
                throw new UsageException("Missing value for " + flag);
            }
            return new Value(inlineValue, index);
        }
        if (index + 1 >= args.length) {
            throw new UsageException("Missing value for " + flag);
        }
        return new Value(args[index + 1], index + 1);
    }

    private record Value(String text, int lastIndex) {}
}

final class UsageException extends RuntimeException {
    UsageException(String message) {
        super(message);
    }
}
