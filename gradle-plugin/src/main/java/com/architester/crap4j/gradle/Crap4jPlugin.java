package com.architester.crap4j.gradle;

import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.tasks.JacocoReport;

/** Registers the com.architester.crap4j extension and verification tasks. */
public final class Crap4jPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        Crap4jExtension extension = project.getExtensions().create(
                "crap4j", Crap4jExtension.class, project.getObjects());
        extension.conventions();

        TaskProvider<CrapCheck> check = registerAnalysis(
                project, "crapCheck", CrapCheck.class, extension);
        TaskProvider<CrapReport> report = registerAnalysis(
                project, "crapReport", CrapReport.class, extension);
        TaskProvider<CrapBaseline> baseline = registerBaseline(project, "crapBaseline", CrapBaseline.class, extension);
        TaskProvider<CrapBaselineTighten> tighten = registerBaseline(
                project, "crapBaselineTighten", CrapBaselineTighten.class, extension);

        project.getPluginManager().withPlugin("java", ignored -> {
            project.getPluginManager().apply(JacocoPlugin.class);
            TaskProvider<JacocoReport> jacoco = project.getTasks().named("jacocoTestReport", JacocoReport.class);
            jacoco.configure(task -> {
                task.getReports().getXml().getRequired().set(true);
                task.getReports().getXml().getRequired().disallowChanges();
                task.getReports().getXml().getOutputLocation().convention(
                        project.getLayout().getBuildDirectory().file(
                                "reports/jacoco/test/jacocoTestReport.xml"));
            });
            Provider<RegularFile> jacocoXml = jacoco.map(task ->
                    task.getReports().getXml().getOutputLocation().get());
            extension.getJacocoXml().convention(jacocoXml);
            check.configure(task -> task.dependsOn(jacoco));
            report.configure(task -> task.dependsOn(jacoco));
            baseline.configure(task -> task.dependsOn(jacoco));
            tighten.configure(task -> task.dependsOn(jacoco));
            project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME).configure(task ->
                    task.dependsOn(extension.getAttachToCheck().map(attach ->
                            attach ? List.of(check) : List.of())));
        });
    }

    private static <T extends AbstractCrapAnalysisTask> TaskProvider<T> registerAnalysis(
            Project project,
            String name,
            Class<T> type,
            Crap4jExtension extension) {
        return project.getTasks().register(name, type, task -> {
            applyTaskConventions(task, extension);
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Analyzes JaCoCo coverage with the CRAP metric.");
            task.getBaseline().convention(extension.getBaseline());
            task.getConventionalBaseline().convention(
                    project.getLayout().getProjectDirectory().file("crap4j-baseline.json"));
            task.getAdvisory().convention(extension.getAdvisory());
            task.getJsonEnabled().convention(extension.getFormats().getJson());
            task.getJunitXmlEnabled().convention(extension.getFormats().getJunitXml());
            task.getJsonReport().convention(project.getLayout().getBuildDirectory()
                    .file("reports/crap4j/" + name + "/report.json"));
            task.getJunitXmlReport().convention(project.getLayout().getBuildDirectory()
                    .file("reports/crap4j/" + name + "/junit.xml"));
        });
    }

    private static <T extends AbstractCrapTask> TaskProvider<T> registerBaseline(
            Project project, String name, Class<T> type, Crap4jExtension extension) {
        return project.getTasks().register(name, type, task -> {
            applyTaskConventions(task, extension);
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Maintains the crap4j baseline.");
            RegularFile conventionalBaseline = project.getLayout().getProjectDirectory()
                    .file("crap4j-baseline.json");
            if (task instanceof CrapBaseline baseline) {
                baseline.getBaseline().convention(extension.getBaseline().orElse(conventionalBaseline));
            } else if (task instanceof CrapBaselineTighten tighten) {
                tighten.getBaseline().convention(extension.getBaseline().orElse(conventionalBaseline));
            }
        });
    }

    private static void applyTaskConventions(AbstractCrapTask task, Crap4jExtension extension) {
        task.getJacocoXml().convention(extension.getJacocoXml());
        task.getThreshold().convention(extension.getThreshold());
        task.getComplexityCap().convention(extension.getComplexityCap());
        task.getRequireTightBaseline().convention(extension.getRequireTightBaseline());
        task.getExcludes().convention(extension.getExcludes());
        task.getExcludeClasses().convention(extension.getExcludeClasses());
        task.getUseDefaultExclusions().convention(extension.getUseDefaultExclusions());
    }

    static String toolVersion() {
        String version = Crap4jPlugin.class.getPackage().getImplementationVersion();
        return version == null ? "development" : version;
    }
}
