plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":common"))

    api(KotlinX.coroutines.jdk8)

    api("org.neo4j.driver:neo4j-java-driver:_")
    implementation("org.neo4j:neo4j-slf4j:_")
}