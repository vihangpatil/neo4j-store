plugins {
  `java-library`
  kotlin("jvm")
  kotlin("kapt")
}

repositories {
  // docker-compose-junit is published on bintray
  maven { url = uri("https://dl.bintray.com/palantir/releases") }
}

group = "dev.vihang.iam"

dependencies {
  implementation(project(":dsl"))
  compileOnly(project(":dsl-model-annotation"))
  kapt(project(":dsl-annotation-processor"))

  testImplementation("com.palantir.docker.compose:docker-compose-junit-jupiter:${Version.dockerComposeJunit}")

  testImplementation(kotlin("test"))
  testImplementation("org.junit.jupiter:junit-jupiter:${Version.junit5}")
  testImplementation("org.amshove.kluent:kluent:${Version.kluent}")
}

tasks.test {
  useJUnitPlatform()
}