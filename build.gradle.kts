import org.gradle.api.tasks.Exec
import org.cyclonedx.gradle.CyclonedxDirectTask

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
