plugins {
  `java-library`
  kotlin("jvm")
}

dependencies {
  api(kotlin("stdlib-jdk8"))
  api("org.slf4j:slf4j-api:${Version.slf4j}")

  api("com.fasterxml.jackson.module:jackson-module-kotlin:${Version.jackson}")

  runtimeOnly("javax.xml.bind:jaxb-api:${Version.jaxb}")
  runtimeOnly("javax.activation:activation:${Version.javaxActivation}")
}