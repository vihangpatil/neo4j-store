plugins {
    `java-library`
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    implementation(project(":dsl"))
    compileOnly(project(":dsl-model-annotation"))
    kapt(project(":dsl-annotation-processor"))
}