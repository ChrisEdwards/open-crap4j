plugins {
    id("crap4j.java-conventions")
    `java-gradle-plugin`
}

base {
    archivesName = "crap4j-gradle-plugin"
}

dependencies {
    implementation(project(":core"))
}

val functionalTest = sourceSets.create("functionalTest")

functionalTest.resources.srcDir(rootProject.layout.projectDirectory.dir("test-fixtures"))

gradlePlugin {
    plugins {
        create("crap4j") {
            id = "com.architester.crap4j"
            implementationClass = "com.architester.crap4j.gradle.Crap4jPlugin"
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
    shouldRunAfter(tasks.test)
}

tasks.check {
    dependsOn(functionalTestTask)
}
