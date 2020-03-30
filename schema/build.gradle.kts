plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  api(project(":client"))
  api(project(":common"))
  api(project(":error"))
  api(project(":model"))

  api("org.neo4j.driver:neo4j-java-driver:${Version.neo4jDriver}")
  implementation("org.neo4j:neo4j-slf4j:${Version.neo4j}")

  api("io.arrow-kt:arrow-fx:${Version.arrow}")
  api("io.arrow-kt:arrow-syntax:${Version.arrow}")
}