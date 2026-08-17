plugins {
    id("crap4j.java-conventions")
    id("com.vanniktech.maven.publish")
}

base {
    archivesName = "crap4j-core"
}

mavenPublishing {
    coordinates("com.architester", "crap4j-core", project.version.toString())
    publishToMavenCentral()
    pom {
        name.set("crap4j core")
        description.set("A zero-runtime-dependency Java library for evaluating CRAP scores from JaCoCo XML reports.")
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
