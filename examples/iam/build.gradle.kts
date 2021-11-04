plugins {
    `java-library`
    kotlin("jvm")
    kotlin("kapt")
}

group = "dev.vihang.iam"

dependencies {
    implementation(project(":dsl"))
    compileOnly(project(":dsl-model-annotation"))
    kapt(project(":dsl-annotation-processor"))

    testImplementation("org.testcontainers:junit-jupiter:${Version.testcontainers}")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:${Version.junit5}")
    testImplementation("org.amshove.kluent:kluent:${Version.kluent}")

    testRuntimeOnly("ch.qos.logback:logback-classic:${Version.logback}")
}

tasks.test {
    useJUnitPlatform()
}