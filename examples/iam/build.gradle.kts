plugins {
    `java-library`
    kotlin("jvm")
    id("com.google.devtools.ksp") version Version.kotlinSymbolProcessing
}

group = "dev.vihang.iam"

dependencies {
    implementation(project(":dsl"))
    compileOnly(project(":dsl-model-annotation"))
    ksp(project(":dsl-annotation-processor"))

    testImplementation("org.testcontainers:junit-jupiter:${Version.testcontainers}")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:${Version.junit5}")
    testImplementation("org.amshove.kluent:kluent:${Version.kluent}")

    testRuntimeOnly("ch.qos.logback:logback-classic:${Version.logback}")
}

tasks.test {
    useJUnitPlatform()
}