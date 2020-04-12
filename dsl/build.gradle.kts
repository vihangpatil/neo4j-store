plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  api(project(":dsl-model"))
  api(project(":schema")) // TODO change from api to implementation
}