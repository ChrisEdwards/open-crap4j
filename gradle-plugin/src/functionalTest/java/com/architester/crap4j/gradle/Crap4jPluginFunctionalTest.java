package com.architester.crap4j.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class Crap4jPluginFunctionalTest {
    private final String gradleVersion;

    @TempDir
    Path projectDirectory;

    Crap4jPluginFunctionalTest(String gradleVersion) {
        this.gradleVersion = gradleVersion;
    }

    @BeforeEach
    void createProject() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'fixture'\n");
        try (InputStream fixture = getClass().getResourceAsStream("/jacoco/report.xml")) {
            Files.copy(fixture, projectDirectory.resolve("jacoco.xml"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void crapCheckWritesReportsBeforeFailingOnViolations() throws IOException {
        writeAggregatorBuild(1.0d, 1);

        BuildResult result = runAndFail(gradleVersion, "crapCheck");

        Path outputDirectory = projectDirectory.resolve("build/reports/crap4j/crapCheck");
        assertThat(result.getOutput()).contains("CRAP violations found");
        assertThat(outputDirectory.resolve("report.json"))
                .content().contains("\"status\": \"fail\"");
        assertThat(outputDirectory.resolve("junit.xml"))
                .content().contains("<testsuite name=\"crap4j.crapCheck\"");
    }

    @Test
    void coveringBaselineLetsCrapCheckPass() throws IOException {
        writeAggregatorBuild(1.0d, 1);

        run(gradleVersion, "crapBaseline");
        BuildResult result = run(gradleVersion, "crapCheck");

        assertThat(result.task(":crapCheck").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(projectDirectory.resolve("build/reports/crap4j/crapCheck/report.json"))
                .content().contains("\"status\": \"pass\"")
                .contains("\"status\": \"baselined\"");
    }

    @Test
    void crapReportIsAlwaysAdvisory() throws IOException {
        writeAggregatorBuild(1.0d, 1);

        BuildResult result = run(gradleVersion, "crapReport");

        assertThat(result.task(":crapReport").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).contains(
                "\n\nopen-crap4j - Report for module: fixture\n\nthreshold");
        assertThat(projectDirectory.resolve("build/reports/crap4j/crapReport/report.json"))
                .content().contains("\"status\": \"advisory\"")
                .contains("\"advisory\": true");
    }

    @Test
    void secondIdenticalRunReusesConfigurationCacheAndIsUpToDate() throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'com.architester.crap4j'
                }

                crap4j {
                    jacocoXml.set(layout.projectDirectory.file('jacoco.xml'))
                }
                """);

        run(gradleVersion, "crapReport", "--configuration-cache");
        BuildResult second = run(gradleVersion, "crapReport", "--configuration-cache");

        assertThat(second.task(":crapReport").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);
        assertThat(second.getOutput()).containsIgnoringCase("configuration cache");
    }

    @Test
    void attachToCheckControlsTheJavaCheckLifecycle() throws IOException {
        writeJavaBuild(false);
        BuildResult detached = run(gradleVersion, "check", "--dry-run");
        assertThat(detached.getOutput()).doesNotContain(":crapCheck SKIPPED");

        writeJavaBuild(true);
        BuildResult attached = run(gradleVersion, "check", "--dry-run");
        assertThat(attached.getOutput()).contains(":jacocoTestReport SKIPPED", ":crapCheck SKIPPED");
    }

    @Test
    void crapBaselineUsesTheConventionPathAndSortsEntries() throws IOException {
        writeAggregatorBuild(1.0d, 1);

        run(gradleVersion, "crapBaseline");

        Path baseline = projectDirectory.resolve("crap4j-baseline.json");
        assertThat(baseline).isRegularFile();
        Matcher entries = Pattern.compile(
                "\"class\": \"([^\"]+)\".*?"
                        + "\"method\": \"([^\"]+)\".*?"
                        + "\"descriptor\": \"([^\"]+)\"",
                Pattern.DOTALL).matcher(Files.readString(baseline));
        List<String> actualOrder = new ArrayList<>();
        while (entries.find()) {
            actualOrder.add(entries.group(1) + "#" + entries.group(2) + "#" + entries.group(3));
        }
        assertThat(actualOrder).isNotEmpty().isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    void explicitlyConfiguredMissingBaselineIsAnError() throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'com.architester.crap4j'
                }

                crap4j {
                    jacocoXml.set(layout.projectDirectory.file('jacoco.xml'))
                    baseline.set(layout.projectDirectory.file('crap4j-baseline.json'))
                }
                """);

        BuildResult result = runAndFail(gradleVersion, "crapReport");

        assertThat(result.getOutput()).contains("crap4j-baseline.json", "doesn't exist");
    }

    @Test
    void baselineExtensionExposesTheConventionPath() throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'com.architester.crap4j'
                }

                tasks.register('printBaselineConvention') {
                    doLast {
                        println crap4j.baseline.get().asFile
                    }
                }
                """);

        BuildResult result = run(gradleVersion, "printBaselineConvention");

        assertThat(result.getOutput()).contains(
                projectDirectory.resolve("crap4j-baseline.json").toString());
    }

    @Test
    void javaProjectsUseJacocoTestReportByConvention() throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.architester.crap4j'
                }

                jacocoTestReport {
                    reports.xml.outputLocation = layout.buildDirectory.file('custom/jacoco.xml')
                }
                """);
        Path jacocoOutput = projectDirectory.resolve("build/custom/jacoco.xml");
        Files.createDirectories(jacocoOutput.getParent());
        Files.copy(projectDirectory.resolve("jacoco.xml"), jacocoOutput);

        BuildResult result = run(gradleVersion, "crapReport");

        assertThat(result.task(":jacocoTestReport")).isNotNull();
        assertThat(result.task(":crapReport").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void javaProjectsCannotDisableJacocoXml() throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.architester.crap4j'
                }

                jacocoTestReport {
                    reports.xml.required = false
                }
                """);

        BuildResult result = runAndFail(gradleVersion, "tasks");

        assertThat(result.getOutput()).contains("required", "cannot be changed");
    }

    @Test
    void formatsAndPerTaskOutputPathsAreConfigurable() throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'com.architester.crap4j'
                }

                crap4j {
                    jacocoXml.set(layout.projectDirectory.file('jacoco.xml'))
                    formats {
                        junitXml.set(false)
                    }
                }

                tasks.named('crapReport') {
                    jsonReport.set(layout.buildDirectory.file('custom/crap.json'))
                    junitXmlReport.set(layout.buildDirectory.file('custom/crap.xml'))
                }
                """);

        run(gradleVersion, "crapReport");

        assertThat(projectDirectory.resolve("build/custom/crap.json")).isRegularFile();
        assertThat(projectDirectory.resolve("build/custom/crap.xml")).doesNotExist();
    }

    @Test
    void crapBaselineTightenRemovesSlackEntries() throws IOException {
        writeAggregatorBuild(1.0d, 1);
        run(gradleVersion, "crapBaseline");

        writeAggregatorBuild(10_000.0d, 10_000);
        run(gradleVersion, "crapBaselineTighten");

        assertThat(projectDirectory.resolve("crap4j-baseline.json"))
                .content().contains("\"entries\": [  ]")
                .doesNotContain("\"class\"");
    }

    private void writeAggregatorBuild(double threshold, int cap) throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'com.architester.crap4j'
                }

                crap4j {
                    jacocoXml.set(layout.projectDirectory.file('jacoco.xml'))
                    threshold.set(%sd)
                    complexityCap.set(%d)
                }
                """.formatted(threshold, cap));
    }

    private void writeJavaBuild(boolean attachToCheck) throws IOException {
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.architester.crap4j'
                }

                crap4j {
                    attachToCheck.set(%s)
                }
                """.formatted(attachToCheck));
    }

    private BuildResult runAndFail(String gradleVersion, String... arguments) {
        try {
            return runner(gradleVersion, arguments).build();
        } catch (UnexpectedBuildFailure failure) {
            return failure.getBuildResult();
        }
    }

    private BuildResult run(String gradleVersion, String... arguments) {
        return runner(gradleVersion, arguments).build();
    }

    private GradleRunner runner(String gradleVersion, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withGradleVersion(gradleVersion)
                .withArguments(arguments)
                .forwardOutput();
    }
}
