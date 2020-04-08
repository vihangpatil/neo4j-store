plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  api(project(":client"))
  api(project(":common"))
  api(project(":error"))
  api(project(":model"))
  api(project(":schema"))
}