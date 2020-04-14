plugins {
  kotlin("jvm")
  kotlin("kapt")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))

  implementation(project(":dsl-model-annotation"))

  implementation("com.squareup:kotlinpoet:${Version.kotlinPoet}")
}
