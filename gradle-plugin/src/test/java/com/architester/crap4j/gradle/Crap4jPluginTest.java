package com.architester.crap4j.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.junit.jupiter.api.Test;

class Crap4jPluginTest {
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
}
