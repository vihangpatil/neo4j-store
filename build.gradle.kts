import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    java
    id("com.github.ben-manes.versions") version Version.versionsPlugin
    jacoco
    kotlin("jvm") version Version.kotlin
    id("com.github.johnrengelman.shadow") version Version.shadowJarPlugin apply false
    idea
}

allprojects {
    apply(plugin = "jacoco")

    group = "dev.vihang.neo4j-store"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        jcenter()
        // docker-compose-junit is published on bintray
        maven("https://dl.bintray.com/palantir/releases")
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_16.toString()
        targetCompatibility = JavaVersion.VERSION_16.toString()
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_16.majorVersion
        }
    }
}

idea {
    targetVersion = JavaVersion.VERSION_16.majorVersion
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
