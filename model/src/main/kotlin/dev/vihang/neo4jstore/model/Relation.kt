package dev.vihang.neo4jstore.model

import kotlin.reflect.KClass

class Relation(
        val name: String,
        val from: KClass<out HasId>,
        val to: KClass<out HasId>,
        val isUnique: Boolean = true)
