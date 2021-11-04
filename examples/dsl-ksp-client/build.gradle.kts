plugins {
    `java-library`
    kotlin("jvm")
    id("com.google.devtools.ksp") version Version.kotlinSymbolProcessing
}

dependencies {
    implementation(project(":dsl"))
    compileOnly(project(":dsl-model-annotation"))
    ksp(project(":dsl-annotation-processor"))
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}