package dev.vihang.neo4jstore.dsl.model

import dev.vihang.neo4jstore.schema.model.HasId
import dev.vihang.neo4jstore.schema.model.Relation
import kotlin.reflect.KClass

open class EntityContext<E : HasId>(val entityClass: KClass<E>, open val id: String)

data class RelatedFromClause<FROM : HasId, TO : HasId>(
        val relation: Relation<FROM, *, TO>,
        val fromId: String)

data class RelatedToClause<FROM : HasId, TO : HasId>(
        val relation: Relation<FROM, *, TO>,
        val toId: String)

data class RelationExpression<FROM : HasId, RELATION : Any, TO : HasId>(
        val relation: Relation<FROM, RELATION, TO>,
        val fromId: String,
        val toId: String,
        val dataClass: RELATION? = null)
