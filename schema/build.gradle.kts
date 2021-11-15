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

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:${Version.junit5}")
    testImplementation("org.testcontainers:junit-jupiter:${Version.testcontainers}")
    testImplementation("org.amshove.kluent:kluent:${Version.kluent}")

    testRuntimeOnly("ch.qos.logback:logback-classic:${Version.logback}")
}

tasks.test {
    useJUnitPlatform()
}