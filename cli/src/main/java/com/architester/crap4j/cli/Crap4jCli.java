package com.architester.crap4j.cli;

import com.architester.crap4j.core.Baseline;
import com.architester.crap4j.core.BaselineGate;
import com.architester.crap4j.core.BaselineJson;
import com.architester.crap4j.core.BaselineOperations;
import com.architester.crap4j.core.ChangedFileSelection;
import com.architester.crap4j.core.ChangedFileSelector;
import com.architester.crap4j.core.ConfigWarning;
import com.architester.crap4j.core.CoverageSelection;
import com.architester.crap4j.core.Exclusions;
import com.architester.crap4j.core.GateConfig;
import com.architester.crap4j.core.GateResult;
import com.architester.crap4j.core.JacocoReport;
import com.architester.crap4j.core.JacocoXmlParser;
import com.architester.crap4j.core.JsonReportWriter;
import com.architester.crap4j.core.JunitXmlReportWriter;
import com.architester.crap4j.core.ReportProducer;
import com.architester.crap4j.core.ScoringEngine;
import com.architester.crap4j.core.ScoringResult;
import com.architester.crap4j.core.TextReportOutput;
import com.architester.crap4j.core.TextReportWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Testable implementation of the crap4j command. */
public final class Crap4jCli {
    private static final String CONVENTIONAL_BASELINE = "crap4j-baseline.json";

    public int run(
            String[] args,
            InputStream standardInput,
            PrintStream standardOutput,
            PrintStream standardError,
            Path workingDirectory) {
        try {
            if (args.length == 0 || (args.length == 1 && args[0].equals("--help"))) {
                standardOutput.print(topLevelHelp());
                return 0;
            }
            if (args.length == 1 && args[0].equals("--version")) {
                standardOutput.println("crap4j " + Main.toolVersion());
                return 0;
            }
            if (args.length == 2 && args[1].equals("--help")) {
                CliArguments.Verb verb = CliArguments.Verb.parse(args[0]);
                standardOutput.print(verbHelp(verb));
                return 0;
            }

            CliArguments options = CliArguments.parse(args);
            return execute(options, standardInput, standardOutput, standardError,
                    workingDirectory.toAbsolutePath().normalize());
        } catch (UsageException exception) {
            standardError.println("ERROR: " + exception.getMessage());
            return 1;
        } catch (Exception exception) {
            String message = exception.getMessage();
            standardError.println("ERROR: " + (message == null ? exception.getClass().getSimpleName() : message));
            return 1;
        }
    }

    private static int execute(
            CliArguments options,
            InputStream standardInput,
            PrintStream standardOutput,
            PrintStream standardError,
            Path workingDirectory) throws Exception {
        Path reportFile = resolve(workingDirectory, options.report());
        if (!Files.isRegularFile(reportFile)) {
            throw new UsageException("Report does not exist: " + options.report());
        }
        JacocoReport report = new JacocoXmlParser().parse(reportFile);
        List<String> changedFiles = options.changedFiles().isPresent()
                ? readChangedFiles(options.changedFiles().orElseThrow(), standardInput, workingDirectory)
                : List.of();
        if (options.changedFiles().isPresent()) {
            warnIfOutdated(report, changedFiles, reportFile, workingDirectory, standardError);
            ChangedFileSelection selection = new ChangedFileSelector().select(report, changedFiles);
            report = selection.report();
            if (selection.skippedFiles() > 0) {
                standardError.println("Skipped " + selection.skippedFiles()
                        + (selection.skippedFiles() == 1 ? " changed file" : " changed files")
                        + " not present in the coverage report");
            }
        }

        ScoringResult scoring = new ScoringEngine().score(
                report,
                new Exclusions(
                        options.exclusions(), options.classExclusions(), options.useDefaultExclusions()));
        GateConfig config = new GateConfig(
                options.threshold(), options.complexityCap(), CoverageSelection.BRANCH_PREFERRED,
                options.requireTightBaseline(), options.changedFiles().isPresent());
        BaselineLocation baseline = baselineLocation(options, workingDirectory);

        if (options.verb().writesBaseline()) {
            Baseline created = new BaselineOperations().rebaseline(
                    scoring, config, Main.toolVersion(), Instant.now().toString());
            BaselineJson.write(baseline.path(), created);
            standardOutput.println("Wrote baseline " + baseline.displayName());
            return 0;
        }
        Optional<Baseline> stored = baseline.exists()
                ? Optional.of(BaselineJson.read(baseline.path()))
                : Optional.empty();
        if (options.verb().tightensBaseline()) {
            if (stored.isEmpty()) {
                throw new UsageException("Cannot tighten: no baseline file exists");
            }
            GateResult preflight = new BaselineGate().evaluate(scoring, stored, config);
            printConfigWarnings(preflight, standardError);
            Baseline tightened = new BaselineOperations().tighten(
                    stored.orElseThrow(), scoring, config, Main.toolVersion(), Instant.now().toString());
            BaselineJson.write(baseline.path(), tightened);
            standardOutput.println("Wrote tightened baseline " + baseline.displayName());
            return 0;
        }

        GateResult gate = new BaselineGate().evaluate(scoring, stored, config);
        printConfigWarnings(gate, standardError);
        boolean advisory = options.verb().inherentlyAdvisory() || options.advisory();
        Optional<String> baselineDisplay = stored.map(ignored -> baseline.displayName());
        TextReportOutput text = new TextReportWriter().render(
                gate, config, advisory, baselineDisplay, ReportProducer.CLI, options.showPassing());
        standardError.print(text.diagnostics());

        String json = new JsonReportWriter().write(
                gate, config, Main.toolVersion(), advisory, baselineDisplay);
        boolean jsonOnStdout = options.jsonReport().filter("-"::equals).isPresent();
        if (jsonOnStdout) {
            standardOutput.print(json);
        } else {
            standardOutput.print(text.standardOutput());
            if (options.jsonReport().isPresent()) {
                Files.writeString(resolve(workingDirectory, options.jsonReport().orElseThrow()), json);
            }
        }
        if (options.junitReport().isPresent()) {
            String junit = new JunitXmlReportWriter().write(
                    gate, config, options.verb().commandName(), ReportProducer.CLI);
            Files.writeString(resolve(workingDirectory, options.junitReport().orElseThrow()), junit);
        }
        return options.verb().gates() && !advisory && gate.violations() > 0 ? 2 : 0;
    }

    private static BaselineLocation baselineLocation(
            CliArguments options, Path workingDirectory) {
        String display = options.baseline().orElse(CONVENTIONAL_BASELINE);
        Path path = resolve(workingDirectory, display);
        boolean exists = Files.isRegularFile(path);
        if (options.baseline().isPresent()
                && options.verb().requiresExistingExplicitBaseline()
                && !exists) {
            throw new UsageException("Baseline does not exist: " + display);
        }
        return new BaselineLocation(path, display, exists);
    }

    private static List<String> readChangedFiles(
            String location, InputStream standardInput, Path workingDirectory) throws IOException {
        if (location.equals("-")) {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(standardInput, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
        Path path = resolve(workingDirectory, location);
        if (!Files.isRegularFile(path)) {
            throw new UsageException("Changed-files input does not exist: " + location);
        }
        return Files.readAllLines(path);
    }

    private static void warnIfOutdated(
            JacocoReport report,
            List<String> changedFiles,
            Path reportFile,
            Path workingDirectory,
            PrintStream standardError) throws IOException {
        long reportModified = Files.getLastModifiedTime(reportFile).toMillis();
        ChangedFileSelector selector = new ChangedFileSelector();
        for (String changedFile : changedFiles) {
            if (selector.select(report, List.of(changedFile)).skippedFiles() != 0) {
                continue;
            }
            Path source = resolve(workingDirectory, changedFile.replace('\\', '/'));
            if (Files.isRegularFile(source)
                    && Files.getLastModifiedTime(source).toMillis() > reportModified) {
                standardError.println("WARNING: outdated report; changed source file "
                        + changedFile + " is newer than " + reportFile.getFileName());
            }
        }
    }

    private static void printConfigWarnings(GateResult gate, PrintStream standardError) {
        for (ConfigWarning warning : gate.configWarnings()) {
            String field = warning == ConfigWarning.THRESHOLD_MISMATCH
                    ? "threshold"
                    : "complexity cap";
            standardError.println("WARNING: baseline " + field
                    + " differs from current configuration; gating with current configuration");
        }
    }

    private static Path resolve(Path workingDirectory, String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path.normalize() : workingDirectory.resolve(path).normalize();
    }

    private static String topLevelHelp() {
        return """
                Usage: crap4j <verb> [options]

                Verbs:
                  check     Gate the report and exit 2 on violations
                  report    Render an advisory report
                  baseline  Write or replace a baseline
                  tighten   Shrink an existing baseline

                Run 'crap4j <verb> --help' for verb options.
                """;
    }

    private static String verbHelp(CliArguments.Verb verb) {
        StringBuilder help = new StringBuilder("Usage: crap4j ")
                .append(verb.commandName()).append(" --report <path> [options]\n\n")
                .append("Shared options:\n")
                .append("  --threshold <double>\n")
                .append("  --complexity-cap <int>\n")
                .append("  --exclude <glob>\n")
                .append("  --exclude-class <regex>\n")
                .append("  --use-default-exclusions <true|false>\n")
                .append("  --baseline <path>\n");
        if (verb.acceptsChangedFiles()) {
            help.append("  --changed-files <path|->\n");
        }
        if (verb.acceptsRequireTightBaseline()) {
            help.append("  --require-tight-baseline\n");
        }
        if (verb.acceptsReportOutputs()) {
            help.append("  --show-passing <N>\n")
                    .append("  --json-report <path|->\n")
                    .append("  --junit-report <path>\n");
        }
        if (verb.acceptsAdvisory()) {
            help.append("  --advisory\n");
        }
        return help.toString();
    }

    private record BaselineLocation(Path path, String displayName, boolean exists) {}
}
