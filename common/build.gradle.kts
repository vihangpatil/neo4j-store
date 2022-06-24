plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    api(kotlin("stdlib-jdk8"))
    api("org.slf4j:slf4j-api:_")

    api("com.fasterxml.jackson.module:jackson-module-kotlin:_")

    runtimeOnly("javax.xml.bind:jaxb-api:_")
    runtimeOnly("javax.activation:activation:_")
}