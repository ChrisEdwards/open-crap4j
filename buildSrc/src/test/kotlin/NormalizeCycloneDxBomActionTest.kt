import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class NormalizeCycloneDxBomActionTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun normalize_should_use_published_identity_when_bom_contains_gradle_project_identity() {
        val bom = temporaryDirectory.resolve("bom.json").toFile()
        bom.writeText(
            """
            {
              "bom-ref" : "$SOURCE_PURL",
              "name" : "core",
              "purl" : "$SOURCE_PURL",
              "dependsOn" : [ "$SOURCE_PURL" ]
            }
            """.trimIndent(),
        )

        action().normalize(bom)

        assertThat(bom.readText())
            .doesNotContain(SOURCE_PURL, "\"name\" : \"core\"")
            .contains(PUBLISHED_PURL, "\"name\" : \"crap4j-core\"")
    }

    @Test
    fun normalize_should_leave_bom_unchanged_when_bom_already_contains_published_identity() {
        val bom = temporaryDirectory.resolve("bom.xml").toFile()
        val publishedBom =
            """
            <component bom-ref="$PUBLISHED_PURL">
              <name>crap4j-core</name>
              <purl>$PUBLISHED_PURL</purl>
            </component>
            """.trimIndent()
        bom.writeText(publishedBom)

        action().normalize(bom)

        assertThat(bom.readText()).isEqualTo(publishedBom)
    }

    private fun action() =
        NormalizeCycloneDxBomAction(
            "core",
            "crap4j-core",
            SOURCE_PURL,
            PUBLISHED_PURL,
        )

    private companion object {
        const val SOURCE_PURL = "pkg:maven/com.architester/core@0.1.0?project_path=%3Acore"
        const val PUBLISHED_PURL = "pkg:maven/com.architester/crap4j-core@0.1.0"
    }
}
