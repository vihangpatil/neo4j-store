import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  `java-library`
  kotlin("jvm")
  `maven-publish`
  id("com.github.johnrengelman.shadow")
}

dependencies {
  api(project(":dsl-model"))
  api(project(":schema"))
}

tasks.withType<ShadowJar> {
  mergeServiceFiles()
  archiveClassifier.set("")
  dependencies {
    include {
      it.moduleGroup == "dev.vihang.neo4j-store"
    }
  }
}

tasks.build {
  dependsOn(tasks.withType<ShadowJar>())
}

publishing {
  publications {
    create<MavenPublication>("shadow") {
      artifact("$buildDir/libs/$name-$version.jar")
      pom {
        name.set("Neo4j Store")
        description.set("Domain Specific Semantic Query Client for Neo4j Graph Database for Kotlin + Gradle projects.")
        url.set("https://github.com/vihangpatil/neo4j-store")
        withXml {
          val deps = asNode().appendNode("dependencies")
          configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts.forEach {
            val module = it.moduleVersion.id
            if (module.group != "dev.vihang.neo4j-store") {
              val dep = deps.appendNode("dependency")
              dep.appendNode("groupId", module.group)
              dep.appendNode("artifactId", module.name)
              dep.appendNode("version", module.version)
            }
          }
        }
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
    maven {
      val releasesRepoUrl = uri("$buildDir/repos/releases")
      val snapshotsRepoUrl = uri("$buildDir/repos/snapshots")
      url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
    }
  }
}
