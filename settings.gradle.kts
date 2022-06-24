plugins {
    id("de.fayard.refreshVersions") version "0.40.1"
}

refreshVersions {
    rejectVersionIf {
        candidate.stabilityLevel != de.fayard.refreshVersions.core.StabilityLevel.Stable
    }
}

rootProject.name = "neo4j-store"

include(

    // common utils
    "common",

    // layer 1
    "client",
    "error",
    "transaction",

    // layer 2
    "schema",
    "schema-model",

    // layer 3
    "dsl",
    "dsl-annotation-processor",
    "dsl-model",
    "dsl-model-annotation",

    // clients using layer 1, 2 and 3.
    "examples:cypher-client", // layer 1 client
    "examples:store-client", // layer 2 client
    "examples:dsl-client", // layer 3 client
    "examples:dsl-ksp-client", // layer 3 client

    // full application
    "examples:iam"
)
