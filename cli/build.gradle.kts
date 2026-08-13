import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

plugins {
    id("crap4j.java-conventions")
    application
}

base {
    archivesName = "crap4j-cli"
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass = "com.architester.crap4j.cli.Main"
}

tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes["Main-Class"] = application.mainClass.get()
    from(configurations.runtimeClasspath.map { classpath ->
        classpath.map { entry -> if (entry.isDirectory) entry else zipTree(entry) }
    })
}
