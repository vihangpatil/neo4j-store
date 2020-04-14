package dev.vihang.neo4jstore.schema.model

import kotlin.reflect.KClass

interface HasId {
    val id: String
}

class Relation<FROM: HasId, RELATION: Any, TO : HasId>(
        val name: String,
        val from: KClass<FROM>,
        val relation: KClass<RELATION>,
        val to: KClass<TO>,
        val isUnique: Boolean = true)

// Need a dummy Void class with no-arg constructor to represent Relations with no properties.
class None