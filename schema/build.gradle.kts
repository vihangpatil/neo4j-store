plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":client"))
    api(project(":error"))
    api(project(":schema-model"))

    api("org.neo4j.driver:neo4j-java-driver:${Version.neo4jDriver}")
    implementation("org.neo4j:neo4j-slf4j:${Version.neo4j}")

    api("io.arrow-kt:arrow-core:${Version.arrow}")

    testImplementation("com.palantir.docker.compose:docker-compose-junit-jupiter:${Version.dockerComposeJunit}")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:${Version.junit5}")
    testImplementation("org.amshove.kluent:kluent:${Version.kluent}")
}

tasks.test {
    useJUnitPlatform()
}