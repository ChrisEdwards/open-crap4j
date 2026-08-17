import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport

class Crap4jJavaConventionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)
        project.pluginManager.apply(JacocoPlugin::class.java)

        project.extensions.configure(JavaPluginExtension::class.java) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
            toolchain.vendor.set(JvmVendorSpec.ADOPTIUM)
        }

        project.tasks.withType(JavaCompile::class.java).configureEach {
            options.release.set(17)
        }

        project.dependencies.add("testImplementation", project.dependencies.platform("org.junit:junit-bom:5.11.4"))
        project.dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter")
        project.dependencies.add("testImplementation", "org.assertj:assertj-core:3.27.3")
        project.dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")

        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }

        project.tasks.withType(Jar::class.java).configureEach {
            manifest.attributes["Implementation-Version"] = project.version.toString()
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            dirPermissions { unix("rwxr-xr-x") }
            filePermissions { unix("rw-r--r--") }
        }

        project.tasks.withType(JacocoReport::class.java).configureEach {
            reports.xml.required.set(true)
        }

        val runtimeClasspath = project.configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
        val zeroDependencyCheck = project.tasks.register(
            "zeroDependencyCheck",
            ZeroDependencyCheck::class.java,
        ) {
            description = "Verifies that runtimeClasspath contains only project artifacts."
            group = "verification"
            externalRuntimeArtifacts.set(
                runtimeClasspath.flatMap { configuration ->
                    configuration.incoming.artifacts.resolvedArtifacts.map { artifacts ->
                        artifacts
                            .mapNotNull { artifact ->
                                val component = artifact.id.componentIdentifier
                                component.displayName.takeUnless { component is ProjectComponentIdentifier }
                            }
                            .distinct()
                            .sorted()
                    }
                },
            )
        }

        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
            dependsOn(zeroDependencyCheck)
        }
    }
}
