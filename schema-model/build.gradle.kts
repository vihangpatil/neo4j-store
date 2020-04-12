plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  api(project(":model"))
}