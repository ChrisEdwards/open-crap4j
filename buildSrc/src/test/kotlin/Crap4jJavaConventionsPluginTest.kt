import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class Crap4jJavaConventionsPluginTest {
    @TempDir
    lateinit var projectDir: Path

    @ParameterizedTest(name = "Gradle {0}")
    @ValueSource(strings = ["8.5", "9.6.1"])
    fun zeroDependencyCheck_should_fail_when_externalRuntimeDependencyDeclared(gradleVersion: String) {
        writeBuild(
            """
            dependencies {
                implementation("org.apache.commons:commons-lang3:3.17.0")
            }
            """.trimIndent(),
            includeMavenCentral = true,
        )

        val result = runner(gradleVersion).buildAndFail()

        assertThat(result.output)
            .contains("External runtime dependencies are not allowed")
            .contains("org.apache.commons:commons-lang3:3.17.0")
        assertThat(result.task(":zeroDependencyCheck")?.outcome).isEqualTo(TaskOutcome.FAILED)
    }

    @ParameterizedTest(name = "Gradle {0}")
    @ValueSource(strings = ["8.5", "9.6.1"])
    fun zeroDependencyCheck_should_pass_when_noExternalRuntimeDependencies(gradleVersion: String) {
        writeBuild("")

        val result = runner(gradleVersion).build()

        assertThat(result.task(":zeroDependencyCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    }

    private fun writeBuild(body: String, includeMavenCentral: Boolean = false) {
        val repositories = if (includeMavenCentral) {
            "dependencyResolutionManagement { repositories.mavenCentral() }"
        } else {
            ""
        }
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement { repositories.gradlePluginPortal() }
            $repositories
            rootProject.name = "zero-dependency-test"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("crap4j.java-conventions")
            }

            $body
            """.trimIndent(),
        )
    }

    private fun runner(gradleVersion: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withGradleVersion(gradleVersion)
            .withArguments("check", "--stacktrace")
}
