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
    void helpAndVersionUseTheCrap4jIdentity() {
        Result noArgs = run("");
        Result help = run("", "--help");
        Result verbHelp = run("", "check", "--help");
        Result version = run("", "--version");

        assertThat(noArgs.exitCode()).isZero();
        assertThat(noArgs.stdout()).startsWith("Usage: crap4j <verb>");
        assertThat(help.stdout()).contains("check", "report", "baseline", "tighten");
        assertThat(verbHelp.stdout()).startsWith("Usage: crap4j check --report <path>");
        assertThat(verbHelp.stdout()).contains(
                "--report-name <name>", "--github-summary <path>", "--github-annotations");
        assertThat(version.stdout()).startsWith("crap4j ");
    }

    @Test
    void checkPassesWithExitZero() {
        Result clean = run("", "check", "--report", report.toString());
        assertThat(clean.exitCode()).isZero();
        assertThat(clean.stdout()).startsWith(
                "open-crap4j - Report for module: JaCoCo Coverage Report\n\nthreshold");
    }

    @Test
    void reportNameOverridesTheJacocoName() {
        Result clean = run("", "check", "--report", report.toString(),
                "--report-name", "open-crap4j:core");

        assertThat(clean.exitCode()).isZero();
        assertThat(clean.stdout()).startsWith(
                "open-crap4j - Report for module: open-crap4j:core\n\nthreshold");
        assertThat(clean.stdout()).doesNotContain("JaCoCo Coverage Report");
    }

    @Test
    void enforcingCheckUsesExitTwoForViolations() {
        Result failing = run("", "check", "--report", report.toString(),
                "--threshold=1", "--complexity-cap=1");
        assertThat(failing.exitCode()).isEqualTo(2);
        assertThat(failing.stdout()).contains("FAIL:");
    }

    @Test
    void advisoryCheckUsesExitZeroForViolations() {
        Result advisory = run("", "check", "--report", report.toString(),
                "--threshold", "1", "--complexity-cap", "1", "--advisory");
        assertThat(advisory.exitCode()).isZero();
        assertThat(advisory.stdout()).contains("ADVISORY:");
    }

    @Test
    void reportVerbAlwaysUsesExitZero() {
        Result reportOnly = run("", "report", "--report", report.toString(),
                "--threshold=1", "--complexity-cap=1");
        assertThat(reportOnly.exitCode()).isZero();
        assertThat(reportOnly.stdout()).contains("ADVISORY:");
    }

    @Test
    void refusesMissingRequiredReport() {
        assertUsageError(run("", "check"), "--report is required");
    }

    @Test
    void refusesChangedFilesOnBaseline() {
        assertUsageError(run("", "baseline", "--report", report.toString(),
                "--changed-files", "files.txt"), "--changed-files");
    }

    @Test
    void refusesChangedFilesOnTighten() {
        assertUsageError(run("", "tighten", "--report", report.toString(),
                "--changed-files=files.txt"), "--changed-files");
    }

    @Test
    void refusesTightBaselineWithChangedFilesOnCheck() {
        assertUsageError(run("", "check", "--report", report.toString(),
                "--changed-files", "-", "--require-tight-baseline"),
                "--require-tight-baseline");
    }

    @Test
    void refusesTightBaselineWithChangedFilesOnReport() {
        assertUsageError(run("", "report", "--report", report.toString(),
                "--changed-files=-", "--require-tight-baseline"),
                "--require-tight-baseline");
    }

    @Test
    void refusesMissingExplicitBaselineOnCheck() {
        Path missing = work.resolve("missing.json");
        assertUsageError(run("", "check", "--report", report.toString(),
                "--baseline", missing.toString()), "does not exist");
    }

    @Test
    void refusesMissingExplicitBaselineOnReport() {
        Path missing = work.resolve("missing.json");
        assertUsageError(run("", "report", "--report", report.toString(),
                "--baseline=" + missing), "does not exist");
    }

    @Test
    void refusesTightenWithoutABaseline() {
        assertUsageError(run("", "tighten", "--report", report.toString()),
                "no baseline");
    }

    @Test
    void refusesDuplicateScalarFlags() {
        assertUsageError(run("", "check", "--report", report.toString(),
                "--threshold", "10", "--threshold=11"), "Duplicate flag");
    }

    @Test
    void jsonDashIsTheOnlyStdoutAndChangedFilesDashReadsStdin() {
        Result json = run("", "check", "--report", report.toString(), "--json-report", "-");
        Result emptyChangedSet = run("", "check", "--report", report.toString(),
                "--changed-files", "-");

        assertThat(json.exitCode()).isZero();
        assertThat(json.stdout()).startsWith("{").contains("\"formatVersion\": 1");
        assertThat(json.stdout()).doesNotContain("open-crap4j");
        assertThat(emptyChangedSet.exitCode()).isZero();
        assertThat(emptyChangedSet.stdout()).contains("0 methods analyzed");
    }

    @Test
    void writesJsonAndJunitFilesAndReportsUnmatchedChangedFiles() throws Exception {
        Path json = work.resolve("report.json");
        Path junit = work.resolve("junit.xml");
        Result outputs = run("", "check", "--report=" + report,
                "--json-report=" + json, "--junit-report", junit.toString(),
                "--exclude", "**/nothing/**", "--exclude", "**/still-nothing/**",
                "--exclude-class=.*Never", "--use-default-exclusions=false");
        Result skipped = run("README.md\n", "check", "--report", report.toString(),
                "--changed-files=-");

        assertThat(outputs.exitCode()).isZero();
        assertThat(Files.readString(json)).startsWith("{").contains("\"status\"");
        assertThat(Files.readString(junit)).startsWith("<?xml").contains("<testsuites>");
        assertThat(skipped.exitCode()).isZero();
        assertThat(skipped.stderr()).contains("Skipped 1 changed file");
    }

    @Test
    void writesGitHubSummaryAndAnnotations() throws Exception {
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
    void sourceRootRequiresGitHubAnnotations() {
        assertUsageError(run("", "report", "--report", report.toString(),
                "--source-root", "src/main/java"), "--source-root requires");
    }

    @Test
    void githubAnnotationsCannotCorruptJsonStdout() {
        assertUsageError(run("", "report", "--report", report.toString(),
                "--json-report", "-", "--github-annotations"), "cannot be combined");
    }

    @Test
    void baselineAndTightenWriteTheConfiguredFile() throws Exception {
        Path baseline = work.resolve("custom-baseline.json");

        Result created = run("", "baseline", "--report", report.toString(),
                "--baseline", baseline.toString(), "--threshold=1", "--complexity-cap=1");
        Result tightened = run("", "tighten", "--report", report.toString(),
                "--baseline", baseline.toString(), "--threshold=1", "--complexity-cap=1");

        assertThat(created.exitCode()).isZero();
        assertThat(Files.readString(baseline)).contains("\"formatVersion\": 1", "\"entries\"");
        assertThat(tightened.exitCode()).isZero();
    }

    @Test
    void changedFileModeWarnsWhenAMatchedSourceIsNewerThanTheReport() throws Exception {
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
