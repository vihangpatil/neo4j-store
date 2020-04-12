plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  api(project(":model"))
  implementation(project(":schema-model"))
  implementation(project(":schema")) // TODO remove this dependency
}