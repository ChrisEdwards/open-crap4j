import org.gradle.api.tasks.Exec

plugins {
    base
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
