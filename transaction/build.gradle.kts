plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    implementation(project(":common"))

    api("org.neo4j.driver:neo4j-java-driver:${Version.neo4jDriver}")
    api("org.neo4j:neo4j-slf4j:${Version.neo4j}")

    api("io.arrow-kt:arrow-core:${Version.arrow}")
}