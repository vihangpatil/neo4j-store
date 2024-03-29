plugins {
    kotlin("jvm")
    `maven-publish`
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(project(":dsl-model-annotation"))

    implementation(Square.kotlinPoet)

    implementation("com.google.devtools.ksp:symbol-processing-api:_")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs += listOf("-Xopt-in=kotlin.RequiresOptIn")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["kotlin"])
            pom {
                name.set("Neo4j Store")
                description.set("Domain Specific Semantic Query Client for Neo4j Graph Database for Kotlin + Gradle projects.")
                url.set("https://github.com/vihangpatil/neo4j-store")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("vihangpatil")
                        name.set("Vihang Patil")
                        email.set("vihang.patil@gmail.com")
                    }
                }
                scm {
                    url.set("https://github.com/vihangpatil/neo4j-store")
                    connection.set("scm:git:git@github.com:vihangpatil/neo4j-store.git")
                    developerConnection.set("scm:git:git@github.com:vihangpatil/neo4j-store.git")
                }
            }
        }
    }
    repositories {
        val releasesRepoUrl = uri("$buildDir/repos/releases")
        val snapshotsRepoUrl = uri("$buildDir/repos/snapshots")
        val repoUrl = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
        maven(repoUrl)
    }
}
