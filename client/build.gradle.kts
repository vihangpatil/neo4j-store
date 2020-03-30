plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))

  api("org.neo4j.driver:neo4j-java-driver:${Version.neo4jDriver}")
  implementation("org.neo4j:neo4j-slf4j:${Version.neo4j}")
}