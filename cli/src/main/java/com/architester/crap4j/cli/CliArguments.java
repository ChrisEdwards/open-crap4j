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
        Optional<String> junitReport,
        Optional<String> githubSummary,
        boolean githubAnnotations,
        Optional<String> sourceRoot) {
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
        ParseState state = new ParseState(Verb.parse(args[0]));
        for (int index = 1; index < args.length; index++) {
            index = state.consume(args, index);
        }
        return state.result();
    }

    private static void validateVerbFlags(
            Verb verb,
            String changedFiles,
            boolean tight,
            boolean advisory,
            OptionalInt showPassing,
            String reportName,
            String jsonReport,
            String junitReport,
            String githubSummary,
            boolean githubAnnotations,
            String sourceRoot) {
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
        validateReportOutputFlags(
                verb, showPassing, reportName, jsonReport, junitReport,
                githubSummary, githubAnnotations, sourceRoot);
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
            String junitReport,
            String githubSummary,
            boolean githubAnnotations,
            String sourceRoot) {
        boolean hasReportOutput = showPassing.isPresent()
                || reportName != null || jsonReport != null || junitReport != null
                || githubSummary != null || githubAnnotations || sourceRoot != null;
        if (!verb.acceptsReportOutputs() && hasReportOutput) {
            throw new UsageException("Report output flags are not allowed on " + verb.commandName());
        }
        if (sourceRoot != null && !githubAnnotations) {
            throw new UsageException("--source-root requires --github-annotations");
        }
        if (githubAnnotations && "-".equals(jsonReport)) {
            throw new UsageException("--github-annotations cannot be combined with --json-report -");
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

    private static final class ParseState {
        private final Verb verb;
        private final List<String> exclusions = new ArrayList<>();
        private final List<String> classExclusions = new ArrayList<>();
        private final Set<String> seen = new HashSet<>();
        private String report;
        private double threshold = 15.0;
        private int complexityCap = 15;
        private boolean defaults = true;
        private String changedFiles;
        private String baseline;
        private boolean tight;
        private boolean advisory;
        private OptionalInt showPassing = OptionalInt.empty();
        private String reportName;
        private String jsonReport;
        private String junitReport;
        private String githubSummary;
        private boolean githubAnnotations;
        private String sourceRoot;

        private ParseState(Verb verb) {
            this.verb = verb;
        }

        private int consume(String[] args, int index) {
            String argument = args[index];
            if (!argument.startsWith("--")) {
                throw new UsageException("Unexpected argument: " + argument);
            }
            int equals = argument.indexOf('=');
            String flag = equals < 0 ? argument : argument.substring(0, equals);
            String inlineValue = equals < 0 ? null : argument.substring(equals + 1);
            if (flag.equals("--exclude") || flag.equals("--exclude-class")) {
                return consumeRepeatable(args, index, flag, inlineValue);
            }
            if (!seen.add(flag)) {
                throw new UsageException("Duplicate flag: " + flag);
            }
            if (consumeSwitch(flag, inlineValue)) {
                return index;
            }
            Value parsed = value(args, index, flag, inlineValue);
            consumeValue(flag, parsed.text());
            return parsed.lastIndex();
        }

        private int consumeRepeatable(String[] args, int index, String flag, String inlineValue) {
            Value parsed = value(args, index, flag, inlineValue);
            (flag.equals("--exclude") ? exclusions : classExclusions).add(parsed.text());
            return parsed.lastIndex();
        }

        private boolean consumeSwitch(String flag, String inlineValue) {
            boolean switchFlag = flag.equals("--require-tight-baseline")
                    || flag.equals("--advisory") || flag.equals("--github-annotations");
            if (!switchFlag) {
                return false;
            }
            if (inlineValue != null) {
                throw new UsageException(flag + " does not take a value");
            }
            tight |= flag.equals("--require-tight-baseline");
            advisory |= flag.equals("--advisory");
            githubAnnotations |= flag.equals("--github-annotations");
            return true;
        }

        private void consumeValue(String flag, String text) {
            try {
                switch (flag) {
                    case "--report" -> report = text;
                    case "--threshold" -> threshold = Double.parseDouble(text);
                    case "--complexity-cap" -> complexityCap = Integer.parseInt(text);
                    case "--use-default-exclusions" -> defaults = parseBoolean(flag, text);
                    case "--changed-files" -> changedFiles = text;
                    case "--baseline" -> baseline = text;
                    case "--show-passing" -> showPassing = showPassing(text);
                    case "--report-name" -> reportName = text;
                    case "--json-report" -> jsonReport = text;
                    case "--junit-report" -> junitReport = text;
                    case "--github-summary" -> githubSummary = text;
                    case "--source-root" -> sourceRoot = text;
                    default -> throw new UsageException("Unknown flag: " + flag);
                }
            } catch (NumberFormatException exception) {
                throw new UsageException("Invalid value for " + flag + ": " + text);
            }
        }

        private CliArguments result() {
            if (report == null) {
                throw new UsageException("--report is required");
            }
            validateVerbFlags(verb, changedFiles, tight, advisory, showPassing,
                    reportName, jsonReport, junitReport, githubSummary, githubAnnotations, sourceRoot);
            return new CliArguments(
                    verb, report, threshold, complexityCap, List.copyOf(exclusions),
                    List.copyOf(classExclusions), defaults, Optional.ofNullable(changedFiles),
                    Optional.ofNullable(baseline), tight, advisory, showPassing,
                    Optional.ofNullable(reportName), Optional.ofNullable(jsonReport),
                    Optional.ofNullable(junitReport), Optional.ofNullable(githubSummary),
                    githubAnnotations, Optional.ofNullable(sourceRoot));
        }
    }

    private record Value(String text, int lastIndex) {}
}

final class UsageException extends RuntimeException {
    UsageException(String message) {
        super(message);
    }
}
