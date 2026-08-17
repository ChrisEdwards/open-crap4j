package com.architester.crap4j.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Crap4jCliTest {
    @TempDir Path work;
    private Path report;

    @BeforeEach
    void copyFixture() throws Exception {
        report = work.resolve("report.xml");
        Files.copy(Path.of("../test-fixtures/jacoco/report.xml"), report);
    }

    @Test
    void run_should_printUsage_when_noArgs() {
        Result result = run("");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).startsWith("Usage: crap4j <verb>");
    }

    @Test
    void run_should_printUsage_when_helpFlag() {
        Result result = run("", "--help");
        assertThat(result.stdout()).contains("check", "report", "baseline", "tighten");
    }

    @Test
    void run_should_printVerbOptions_when_verbHelp() {
        Result result = run("", "check", "--help");
        assertThat(result.stdout()).startsWith("Usage: crap4j check --report <path>");
        assertThat(result.stdout()).contains(
                "--report-name <name>", "--github-summary <path>", "--github-annotations");
    }

    @Test
    void run_should_printVersion_when_versionFlag() {
        Result result = run("", "--version");
        assertThat(result.stdout()).startsWith("crap4j ");
    }

    @Test
    void check_should_exitZero_when_noViolations() {
        Result result = run("", "check", "--report", report.toString());
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).startsWith(
                "open-crap4j - Report for module: JaCoCo Coverage Report\n\nthreshold");
    }

    @Test
    void check_should_useCustomName_when_reportNameProvided() {
        Result result = run("", "check", "--report", report.toString(),
                "--report-name", "open-crap4j:core");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).startsWith(
                "open-crap4j - Report for module: open-crap4j:core\n\nthreshold");
        assertThat(result.stdout()).doesNotContain("JaCoCo Coverage Report");
    }

    @Test
    void check_should_exitTwo_when_violationsFound() {
        Result result = run("", "check", "--report", report.toString(),
                "--threshold=1", "--complexity-cap=1");
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stdout()).contains("FAIL:");
    }

    @Test
    void check_should_exitZero_when_advisoryMode() {
        Result result = run("", "check", "--report", report.toString(),
                "--threshold", "1", "--complexity-cap", "1", "--advisory");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("ADVISORY:");
    }

    @Test
    void report_should_exitZero_when_violationsPresent() {
        Result result = run("", "report", "--report", report.toString(),
                "--threshold=1", "--complexity-cap=1");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("ADVISORY:");
    }

    @Test
    void parse_should_failWithUsageError_when_reportFlagMissing() {
        assertUsageError(run("", "check"), "--report is required");
    }

    @Test
    void parse_should_failWithUsageError_when_changedFilesOnBaseline() {
        assertUsageError(run("", "baseline", "--report", report.toString(),
                "--changed-files", "files.txt"), "--changed-files");
    }

    @Test
    void parse_should_failWithUsageError_when_changedFilesOnTighten() {
        assertUsageError(run("", "tighten", "--report", report.toString(),
                "--changed-files=files.txt"), "--changed-files");
    }

    @Test
    void parse_should_failWithUsageError_when_tightBaselineWithChangedFilesOnCheck() {
        assertUsageError(run("", "check", "--report", report.toString(),
                "--changed-files", "-", "--require-tight-baseline"),
                "--require-tight-baseline");
    }

    @Test
    void parse_should_failWithUsageError_when_tightBaselineWithChangedFilesOnReport() {
        assertUsageError(run("", "report", "--report", report.toString(),
                "--changed-files=-", "--require-tight-baseline"),
                "--require-tight-baseline");
    }

    @Test
    void execute_should_failWithUsageError_when_explicitBaselineMissingOnCheck() {
        Path missing = work.resolve("missing.json");
        assertUsageError(run("", "check", "--report", report.toString(),
                "--baseline", missing.toString()), "does not exist");
    }

    @Test
    void execute_should_failWithUsageError_when_explicitBaselineMissingOnReport() {
        Path missing = work.resolve("missing.json");
        assertUsageError(run("", "report", "--report", report.toString(),
                "--baseline=" + missing), "does not exist");
    }

    @Test
    void tighten_should_failWithUsageError_when_noBaselineExists() {
        assertUsageError(run("", "tighten", "--report", report.toString()),
                "no baseline");
    }

    @Test
    void parse_should_failWithUsageError_when_duplicateScalarFlags() {
        assertUsageError(run("", "check", "--report", report.toString(),
                "--threshold", "10", "--threshold=11"), "Duplicate flag");
    }

    @Test
    void check_should_outputJsonOnly_when_jsonReportIsDash() {
        Result result = run("", "check", "--report", report.toString(), "--json-report", "-");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).startsWith("{").contains("\"formatVersion\": 1");
        assertThat(result.stdout()).doesNotContain("open-crap4j");
    }

    @Test
    void check_should_analyzeZeroMethods_when_changedFilesDashWithEmptyStdin() {
        Result result = run("", "check", "--report", report.toString(),
                "--changed-files", "-");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).contains("0 methods analyzed");
    }

    @Test
    void check_should_writeJsonAndJunitFiles_when_outputPathsProvided() throws Exception {
        Path json = work.resolve("report.json");
        Path junit = work.resolve("junit.xml");

        Result result = run("", "check", "--report=" + report,
                "--json-report=" + json, "--junit-report", junit.toString(),
                "--exclude", "**/nothing/**", "--exclude", "**/still-nothing/**",
                "--exclude-class=.*Never", "--use-default-exclusions=false");

        assertThat(result.exitCode()).isZero();
        assertThat(Files.readString(json)).startsWith("{").contains("\"status\"");
        assertThat(Files.readString(junit)).startsWith("<?xml").contains("<testsuites>");
    }

    @Test
    void check_should_reportSkippedCount_when_changedFileNotInReport() {
        Result result = run("README.md\n", "check", "--report", report.toString(),
                "--changed-files=-");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).contains("Skipped 1 changed file");
    }

    @Test
    void report_should_writeSummaryAndAnnotations_when_githubOptionsProvided() throws Exception {
        Path summary = work.resolve("summary.md");

        Result result = run("", "report", "--report", report.toString(),
                "--threshold=1", "--complexity-cap=1",
                "--github-summary", summary.toString(),
                "--github-annotations", "--source-root", "src/main/java");

        assertThat(result.exitCode()).isZero();
        assertThat(Files.readString(summary))
                .contains("## CRAP report: JaCoCo Coverage Report")
                .contains("### Highest CRAP scores", "| CRAP | cc | Coverage | Status | Method |");
        assertThat(result.stdout())
                .contains("::error file=src/main/java/")
                .contains("title=CRAP ");
    }

    @Test
    void parse_should_failWithUsageError_when_sourceRootWithoutAnnotations() {
        assertUsageError(run("", "report", "--report", report.toString(),
                "--source-root", "src/main/java"), "--source-root requires");
    }

    @Test
    void parse_should_failWithUsageError_when_annotationsWithJsonDash() {
        assertUsageError(run("", "report", "--report", report.toString(),
                "--json-report", "-", "--github-annotations"), "cannot be combined");
    }

    @Test
    void execute_should_failWithUsageError_when_reportFileDoesNotExist() {
        assertUsageError(run("", "check", "--report", work.resolve("missing.xml").toString()),
                "Report does not exist");
    }

    @Test
    void check_should_usePluralMessage_when_multipleChangedFilesSkipped() throws Exception {
        Path changedFiles = work.resolve("changed.txt");
        Files.writeString(changedFiles, "NoSuch1.java\nNoSuch2.java\n");

        Result result = run("", "check", "--report", report.toString(),
                "--changed-files", changedFiles.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).contains("Skipped 2 changed files");
    }

    @Test
    void baseline_should_writeFile_when_baselinePathProvided() throws Exception {
        Path baseline = work.resolve("custom-baseline.json");

        Result result = run("", "baseline", "--report", report.toString(),
                "--baseline", baseline.toString(), "--threshold=1", "--complexity-cap=1");

        assertThat(result.exitCode()).isZero();
        assertThat(Files.readString(baseline)).contains("\"formatVersion\": 1", "\"entries\"");
    }

    @Test
    void tighten_should_writeFile_when_baselineExists() throws Exception {
        Path baseline = work.resolve("custom-baseline.json");
        run("", "baseline", "--report", report.toString(),
                "--baseline", baseline.toString(), "--threshold=1", "--complexity-cap=1");

        Result result = run("", "tighten", "--report", report.toString(),
                "--baseline", baseline.toString(), "--threshold=1", "--complexity-cap=1");

        assertThat(result.exitCode()).isZero();
    }

    @Test
    void check_should_warnOutdatedReport_when_sourceIsNewerThanReport() throws Exception {
        Path source = work.resolve("Anon.java");
        Files.writeString(source, "class Anon {}");
        Files.setLastModifiedTime(report, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(source, FileTime.fromMillis(2_000));

        Result result = run("Anon.java\n", "check", "--report", report.toString(),
                "--changed-files", "-");

        assertThat(result.exitCode()).isZero();
        assertThat(result.stderr()).contains("WARNING:", "outdated report", "Anon.java");
    }

    private Result run(String stdin, String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = new Crap4jCli().run(
                args,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8),
                work);
        return new Result(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static void assertUsageError(Result result, String message) {
        assertThat(result.exitCode()).isOne();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).contains(message).doesNotContain("\n\n");
    }

    private record Result(int exitCode, String stdout, String stderr) {}
}
