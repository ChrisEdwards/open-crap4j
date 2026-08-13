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

gradlePlugin {
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
