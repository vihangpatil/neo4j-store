package dev.vihang.neo4jstore.examples.model

import dev.vihang.neo4jstore.model.HasId

data class User(
        override val id: String,
        val name: String
) : HasId

data class Role(
        override val id: String,
        val description: String
) : HasId