plugins {
    `java-library`
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

group = "dev.vihang.iam"

dependencies {
    implementation(project(":dsl"))
    compileOnly(project(":dsl-model-annotation"))
    ksp(project(":dsl-annotation-processor"))

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

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}