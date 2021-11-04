plugins {
    `java-library`
    kotlin("jvm")
    kotlin("kapt")
}

dependencies {
    implementation(project(":dsl"))
    compileOnly(project(":dsl-model-annotation"))
    kapt(project(":dsl-annotation-processor"))
}

kapt {
    kotlin {
        kotlinDaemonJvmArgs = listOf(
            "-Dfile.encoding=UTF-8",
            "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=jdk.base/java.util.stream=ALL-UNNAMED",
        )
    }
}