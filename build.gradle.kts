import org.gradle.api.tasks.Exec
import org.cyclonedx.gradle.CyclonedxDirectTask
import org.cyclonedx.model.Component

plugins {
    base
    id("org.cyclonedx.bom") version "3.4.1"
    id("com.vanniktech.maven.publish") version "0.37.0" apply false
    id("com.gradle.plugin-publish") version "2.1.1" apply false
}

allprojects {
    group = "com.architester"
}

subprojects {
    tasks.named<CyclonedxDirectTask>("cyclonedxDirectBom") {
        includeConfigs = listOf("runtimeClasspath")
        testConfigs = emptyList()

        if (project.path == ":cli") {
            componentName.set("crap4j-cli")
            projectType.set(Component.Type.APPLICATION)

            val componentGroup = project.group.toString()
            val componentVersion = project.version.toString()
            val sourceCoreName = "core"
            val publishedCoreName = "crap4j-core"
            val sourceCorePurl =
                "pkg:maven/$componentGroup/$sourceCoreName@$componentVersion?project_path=%3Acore"
            val publishedCorePurl = "pkg:maven/$componentGroup/$publishedCoreName@$componentVersion"

            // CycloneDX derives project dependencies from project.name and ignores both
            // base.archivesName and the Maven publication coordinates.
            doLast(
                NormalizeCycloneDxBomAction(
                    sourceCoreName,
                    publishedCoreName,
                    sourceCorePurl,
                    publishedCorePurl,
                ),
            )
        }
    }
}

val buildLogicTest = tasks.register<Exec>("buildLogicTest") {
    description = "Runs buildSrc tests, including the supported Gradle version matrix."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    workingDir = rootDir
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine("cmd", "/c", "gradlew.bat", "-p", "buildSrc", "test")
    } else {
        commandLine("./gradlew", "-p", "buildSrc", "test")
    }
}

tasks.check {
    dependsOn(buildLogicTest)
}
