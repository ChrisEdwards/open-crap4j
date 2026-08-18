import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("crap4j.java-conventions")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
    id("com.vanniktech.maven.publish")
}

base {
    archivesName = "crap4j-gradle-plugin"
}

dependencies {
    implementation(project(":core"))
    testRuntimeOnly(gradleApi())
}

sourceSets.test { resources.srcDir(rootProject.layout.projectDirectory.dir("test-fixtures")) }

val functionalTest = sourceSets.create("functionalTest")

functionalTest.resources.srcDir(rootProject.layout.projectDirectory.dir("test-fixtures"))

gradlePlugin {
    website.set("https://github.com/ChrisEdwards/open-crap4j")
    vcsUrl.set("https://github.com/ChrisEdwards/open-crap4j")
    plugins {
        create("crap4j") {
            id = "com.architester.crap4j"
            implementationClass = "com.architester.crap4j.gradle.Crap4jPlugin"
            displayName = "crap4j"
            description = "Fails a Gradle build when complex Java methods lack test coverage, scored by the CRAP metric."
            tags.set(listOf("crap", "coverage", "jacoco", "quality", "testing"))
        }
    }
    testSourceSets(functionalTest)
}

configurations[functionalTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[functionalTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "Runs functional tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    // Each fork owns one serial Gradle-version lane; finer parallelism contends on TestKit daemons.
    maxParallelForks = 2
    shouldRunAfter(tasks.test)
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(functionalTestTask)
    executionData(layout.buildDirectory.file("jacoco/functionalTest.exec"))
}

tasks.check {
    dependsOn(functionalTestTask)
}

mavenPublishing {
    coordinates("com.architester", "crap4j-gradle-plugin", project.version.toString())
    publishToMavenCentral()
    pom {
        name.set("crap4j Gradle plugin")
        description.set("A Gradle build gate for CRAP scores calculated from JaCoCo XML reports.")
        inceptionYear.set("2026")
        url.set("https://github.com/ChrisEdwards/open-crap4j")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ChrisEdwards")
                name.set("Chris Edwards")
                url.set("https://github.com/ChrisEdwards")
            }
        }
        scm {
            url.set("https://github.com/ChrisEdwards/open-crap4j")
            connection.set("scm:git:https://github.com/ChrisEdwards/open-crap4j.git")
            developerConnection.set("scm:git:ssh://git@github.com/ChrisEdwards/open-crap4j.git")
        }
    }
}
