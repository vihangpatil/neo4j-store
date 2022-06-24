plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(project(":client"))
    api(project(":error"))
    api(project(":schema-model"))

    api("org.neo4j.driver:neo4j-java-driver:_")
    implementation("org.neo4j:neo4j-slf4j:_")

    api("io.arrow-kt:arrow-core:_")

    testImplementation(kotlin("test-junit5"))
    testImplementation(Testing.junit.jupiter)
    testImplementation("org.amshove.kluent:kluent:_")

    testImplementation("org.testcontainers:junit-jupiter:_")
    testImplementation("org.testcontainers:neo4j:_")

    testRuntimeOnly("ch.qos.logback:logback-classic:_")
}

tasks.test {
    useJUnitPlatform()
}