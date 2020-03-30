rootProject.name = "neo4j-store"

include(
  ":client",
  ":common",
  ":error",
  ":model",
  ":schema",
  ":transaction",
  ":examples:cypher-client",
  ":examples:store-client",
  ":examples:dsl-client",
  ":examples:model-client"
)
