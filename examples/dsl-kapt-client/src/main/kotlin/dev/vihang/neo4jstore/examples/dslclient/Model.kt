package dev.vihang.neo4jstore.examples.dslclient

import dev.vihang.neo4jstore.schema.model.HasId
import dev.vihang.neo4jstore.dsl.model.annotation.Entity
import dev.vihang.neo4jstore.dsl.model.annotation.Relation

@Entity
@Relation(
    name = "HAS_ROLE",
    to = "dev.vihang.neo4jstore.examples.dslclient.Role",
    forwardRelation = "hasRole",
    reverseRelation = "ofUser",
    forwardQuery = "withRole",
    reverseQuery = "ofUser",
)
data class User(
    override val id: String,
    val name: String,
) : HasId {
    // Needed for DSL
    companion object
}

@Entity
data class Role(
    override val id: String,
    val description: String,
) : HasId {
    // Needed for DSL
    companion object
}
