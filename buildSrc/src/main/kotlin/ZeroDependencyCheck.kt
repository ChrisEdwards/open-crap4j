import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ZeroDependencyCheck : DefaultTask() {
    @get:Input
    abstract val externalRuntimeArtifacts: ListProperty<String>

    @TaskAction
    fun verify() {
        val artifacts = externalRuntimeArtifacts.get()
        if (artifacts.isNotEmpty()) {
            throw GradleException(
                "External runtime dependencies are not allowed:\n" +
                    artifacts.joinToString(separator = "\n") { "* $it" },
            )
        }
    }
}
