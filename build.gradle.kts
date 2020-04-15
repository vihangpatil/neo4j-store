import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  base
  java
  id("com.github.ben-manes.versions") version Version.versionsPlugin
  jacoco
  kotlin("jvm") version Version.kotlin
  idea
}

allprojects {
  apply(plugin = "jacoco")

  group = "dev.vihang"
  version = "1.0.0-SNAPSHOT"

  repositories {
    mavenCentral()
    jcenter()
    // docker-compose-junit is published on bintray
    maven { url = uri("https://dl.bintray.com/palantir/releases") }
  }
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_13.majorVersion
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_13
  targetCompatibility = JavaVersion.VERSION_13
}

idea {
  targetVersion = JavaVersion.VERSION_13.majorVersion
}

fun isNonStable(version: String): Boolean {
  val regex = "^[0-9,.v-]+$".toRegex()
  val isStable = regex.matches(version)
  return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
  // Example 1: reject all non stable versions
  rejectVersionIf {
    isNonStable(candidate.version)
  }

  // Example 2: disallow release candidates as upgradable versions from stable versions
  rejectVersionIf {
    isNonStable(candidate.version) && !isNonStable(currentVersion)
  }

  // Example 3: using the full syntax
  resolutionStrategy {
    componentSelection {
      all {
        if (isNonStable(candidate.version) && !isNonStable(currentVersion)) {
          reject("Release candidate")
        }
      }
    }
  }
}