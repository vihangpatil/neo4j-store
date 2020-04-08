rootProject.name = "neo4j-store"

include(
  ":client",
  ":common",
  ":dsl",
  ":error",
  ":model",
  ":schema",
  ":transaction",
  ":examples:cypher-client",
  ":examples:dsl-client",
  ":examples:store-client",
  ":examples:model-client"
)
