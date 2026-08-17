import org.gradle.api.Action
import org.gradle.api.Task
import java.io.File
import java.io.Serializable

class NormalizeCycloneDxBomAction(
    private val sourceName: String,
    private val publishedName: String,
    private val sourcePurl: String,
    private val publishedPurl: String,
) : Action<Task>, Serializable {
    override fun execute(task: Task) {
        val bomFiles = task.outputs.files.files.filter { it.isFile && it.extension in setOf("json", "xml") }
        check(bomFiles.isNotEmpty()) { "CycloneDX task ${task.path} produced no JSON or XML output" }
        bomFiles.forEach(::normalize)
    }

    internal fun normalize(bom: File) {
        val original = bom.readText()
        if (sourcePurl !in original) {
            check(publishedPurl in original) {
                "Neither the Gradle nor published component identity was found in $bom"
            }
            return
        }

        val sourceNameToken = nameToken(bom, sourceName)
        check(sourceNameToken in original) {
            "Component name $sourceName was not found in $bom"
        }

        bom.writeText(
            original
                .replace(sourcePurl, publishedPurl)
                .replace(sourceNameToken, nameToken(bom, publishedName)),
        )
    }

    private fun nameToken(bom: File, name: String): String =
        when (bom.extension) {
            "json" -> "\"name\" : \"$name\""
            "xml" -> "<name>$name</name>"
            else -> error("Unsupported CycloneDX output format: $bom")
        }

    private companion object {
        private const val serialVersionUID = 1L
    }
}
