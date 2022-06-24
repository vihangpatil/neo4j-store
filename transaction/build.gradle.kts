plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    implementation(project(":common"))

    api("org.neo4j.driver:neo4j-java-driver:_")
    api("org.neo4j:neo4j-slf4j:_")

    api("io.arrow-kt:arrow-core:_")
}