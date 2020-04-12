package dev.vihang.neo4jstore.examples.dslclient

import dev.vihang.neo4jstore.model.HasId

data class User(
        override val id: String,
        val name: String
) : HasId {
    // Needed for DSL
    companion object
}

data class Role(
        override val id: String,
        val description: String
) : HasId {
    // Needed for DSL
    companion object
}
