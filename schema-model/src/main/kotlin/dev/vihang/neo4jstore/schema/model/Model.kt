package dev.vihang.neo4jstore.schema.model

import dev.vihang.neo4jstore.model.HasId
import kotlin.reflect.KClass

class Relation<FROM: HasId, TO : HasId>(
        val name: String,
        val from: KClass<FROM>,
        val to: KClass<TO>,
        val isUnique: Boolean = true)

// Need a dummy Void class with no-arg constructor to represent Relations with no properties.
class None