import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  base
  java
  id("com.github.ben-manes.versions") version Version.versionsPlugin
  jacoco
  kotlin("jvm") version Version.kotlin apply false
  idea
}

allprojects {
  apply(plugin = "jacoco")

  group = "dev.vihang"
  version = "1.0.0-SNAPSHOT"

  repositories {
    mavenCentral()
    jcenter()
  }
}

subprojects {
  tasks.withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_13.majorVersion
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_14
  targetCompatibility = JavaVersion.VERSION_14
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