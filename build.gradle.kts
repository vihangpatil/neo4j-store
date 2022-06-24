import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    base
    java
    jacoco
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") apply false
    idea
}

allprojects {
    apply(plugin = "jacoco")

    group = "dev.vihang.neo4j-store"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.majorVersion
        }
    }
}

idea {
    targetVersion = JavaVersion.VERSION_17.majorVersion
}