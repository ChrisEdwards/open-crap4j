package com.architester.crap4j.gradle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Crap4jPluginTest {
    @TempDir
    Path work;

    private Path jacocoXml;

    @BeforeEach
    void copyFixture() throws IOException {
        try (InputStream fixture = getClass().getResourceAsStream("/jacoco/report.xml")) {
            jacocoXml = work.resolve("jacoco.xml");
            Files.copy(fixture, jacocoXml, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void applyingToAJavaProjectRegistersThePluginInterface() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");

        project.getPluginManager().apply(Crap4jPlugin.class);

        assertThat(project.getPlugins().hasPlugin(JacocoPlugin.class)).isTrue();
        assertThat(project.getExtensions().findByType(Crap4jExtension.class)).isNotNull();
        assertThat(project.getTasks().getNames())
                .contains("crapCheck", "crapReport", "crapBaseline", "crapBaselineTighten");
        boolean crapCheckAttached = project.getTasks().named("check").get().getTaskDependencies()
                .getDependencies(project.getTasks().named("check").get()).stream()
                .anyMatch(task -> task.getName().equals("crapCheck"));
        assertThat(crapCheckAttached).isFalse();
    }

    @Test
    void reportNameUsesTheQualifiedGradleProjectIdentity() {
        Project root = ProjectBuilder.builder().withName("open-crap4j").build();
        Project module = ProjectBuilder.builder().withName("core").withParent(root).build();

        root.getPluginManager().apply(Crap4jPlugin.class);
        module.getPluginManager().apply(Crap4jPlugin.class);

        assertThat(((CrapReport) root.getTasks().getByName("crapReport"))
                .getReportName().get()).isEqualTo("open-crap4j");
        assertThat(((CrapReport) module.getTasks().getByName("crapReport"))
                .getReportName().get()).isEqualTo("open-crap4j:core");
    }

    @Test
    void attachToCheckWiresCheckDependency() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(Crap4jPlugin.class);
        Crap4jExtension extension = project.getExtensions().getByType(Crap4jExtension.class);
        extension.getAttachToCheck().set(true);

        boolean attached = project.getTasks().named("check").get().getTaskDependencies()
                .getDependencies(project.getTasks().named("check").get()).stream()
                .anyMatch(task -> task.getName().equals("crapCheck"));
        assertThat(attached).isTrue();
    }

    @Test
    void registerBaselineSetsConventionForBothTaskTypes() {
        Project project = projectWithPlugin();

        CrapBaseline baseline = (CrapBaseline) project.getTasks().getByName("crapBaseline");
        CrapBaselineTighten tighten = (CrapBaselineTighten) project.getTasks().getByName("crapBaselineTighten");

        assertThat(baseline.getBaseline().get().getAsFile().getName())
                .isEqualTo("crap4j-baseline.json");
        assertThat(tighten.getBaseline().get().getAsFile().getName())
                .isEqualTo("crap4j-baseline.json");
    }

    @Test
    void registerAnalysisUsesConventionalBaselineWhenExtensionIsUnset() {
        Project project = projectWithPlugin();

        CrapReport report = (CrapReport) project.getTasks().getByName("crapReport");

        assertThat(report.getBaseline().isPresent()).isFalse();
        assertThat(report.getConventionalBaseline().get().getAsFile().getName())
                .isEqualTo("crap4j-baseline.json");
    }

    @Test
    void registerAnalysisUsesExplicitBaselineFromExtension() {
        Project project = projectWithPlugin();
        Crap4jExtension extension = project.getExtensions().getByType(Crap4jExtension.class);
        RegularFile explicit = project.getLayout().getProjectDirectory().file("my-baseline.json");
        extension.getBaseline().set(explicit);

        CrapReport report = (CrapReport) project.getTasks().getByName("crapReport");

        assertThat(report.getBaseline().get().getAsFile().getName())
                .isEqualTo("my-baseline.json");
    }

    @Test
    void analyzeProducesReportOutput() throws IOException {
        Project project = projectWithPlugin();
        Crap4jExtension extension = project.getExtensions().getByType(Crap4jExtension.class);
        extension.getJacocoXml().set(jacocoXml.toFile());

        CrapReport report = (CrapReport) project.getTasks().getByName("crapReport");
        report.report();

        Path jsonReport = report.getJsonReport().get().getAsFile().toPath();
        assertThat(jsonReport).isRegularFile();
        assertThat(Files.readString(jsonReport))
                .contains("\"advisory\": true")
                .contains("\"status\": \"pass\"");
    }

    @Test
    void analyzeWritesJunitXmlWhenEnabled() throws IOException {
        Project project = projectWithPlugin();
        Crap4jExtension extension = project.getExtensions().getByType(Crap4jExtension.class);
        extension.getJacocoXml().set(jacocoXml.toFile());

        CrapReport report = (CrapReport) project.getTasks().getByName("crapReport");
        report.report();

        Path junitReport = report.getJunitXmlReport().get().getAsFile().toPath();
        assertThat(junitReport).isRegularFile();
        assertThat(Files.readString(junitReport))
                .contains("<testsuite name=\"crap4j.crapReport\"");
    }

    @Test
    void analyzeSkipsJsonAndJunitWhenDisabled() {
        Project project = projectWithPlugin();
        Crap4jExtension extension = project.getExtensions().getByType(Crap4jExtension.class);
        extension.getJacocoXml().set(jacocoXml.toFile());
        extension.getFormats().getJson().set(false);
        extension.getFormats().getJunitXml().set(false);

        CrapReport report = (CrapReport) project.getTasks().getByName("crapReport");
        report.report();

        assertThat(report.getJsonReport().get().getAsFile()).doesNotExist();
        assertThat(report.getJunitXmlReport().get().getAsFile()).doesNotExist();
    }

    @Test
    void analyzeReadsBaselineWhenPresent() throws IOException {
        Project project = projectWithPlugin();
        Crap4jExtension extension = project.getExtensions().getByType(Crap4jExtension.class);
        extension.getJacocoXml().set(jacocoXml.toFile());
        extension.getFormats().getJson().set(false);
        extension.getFormats().getJunitXml().set(false);
        Path baselineFile = project.getProjectDir().toPath().resolve("crap4j-baseline.json");
        Files.writeString(baselineFile, """
                {
                  "formatVersion": 1,
                  "toolVersion": "0.1.0",
                  "generated": "2026-01-01T00:00:00Z",
                  "coverageSelection": "branch-preferred",
                  "threshold": 15.0,
                  "complexityCap": 15,
                  "entries": []
                }
                """);

        CrapReport report = (CrapReport) project.getTasks().getByName("crapReport");
        report.report();

        assertThat(report.getJsonReport().get().getAsFile()).doesNotExist();
    }

    @Test
    void crapCheckThrowsOnViolations() {
        Project project = projectWithPlugin();
        Crap4jExtension extension = project.getExtensions().getByType(Crap4jExtension.class);
        extension.getJacocoXml().set(jacocoXml.toFile());
        extension.getThreshold().set(1.0d);
        extension.getComplexityCap().set(1);

        CrapCheck check = (CrapCheck) project.getTasks().getByName("crapCheck");

        assertThatThrownBy(check::check)
                .hasMessageContaining("CRAP violations found");
    }

    @Test
    void crapCheckPassesInAdvisoryMode() {
        Project project = projectWithPlugin();
        Crap4jExtension extension = project.getExtensions().getByType(Crap4jExtension.class);
        extension.getJacocoXml().set(jacocoXml.toFile());
        extension.getThreshold().set(1.0d);
        extension.getComplexityCap().set(1);
        extension.getAdvisory().set(true);
        extension.getFormats().getJson().set(false);
        extension.getFormats().getJunitXml().set(false);

        CrapCheck check = (CrapCheck) project.getTasks().getByName("crapCheck");
        check.check();
    }

    private Project projectWithPlugin() {
        Project project = ProjectBuilder.builder().withProjectDir(work.toFile()).build();
        project.getPluginManager().apply(Crap4jPlugin.class);
        return project;
    }
}
