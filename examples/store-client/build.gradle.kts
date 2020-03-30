plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(project(":examples:model-client"))
  implementation(project(":schema"))
}