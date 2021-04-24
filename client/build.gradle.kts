plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":common"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${Version.kotlinCoroutine}")

    api("org.neo4j.driver:neo4j-java-driver:${Version.neo4jDriver}")
    implementation("org.neo4j:neo4j-slf4j:${Version.neo4j}")
}