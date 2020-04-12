package dev.vihang.neo4jstore.examples.storeclient

import dev.vihang.neo4jstore.model.HasId
import dev.vihang.neo4jstore.schema.model.Relation

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

val hasRoleRelation: Relation<User, Role> = Relation(
        name = "HAS_ROLE",
        from = User::class,
        to = Role::class
)
