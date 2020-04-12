package dev.vihang.neo4jstore.dsl.model

import dev.vihang.neo4jstore.model.HasId
import dev.vihang.neo4jstore.schema.RelationType
import kotlin.reflect.KClass

open class EntityContext<E : HasId>(val entityClass: KClass<E>, open val id: String)

data class RelatedFromClause<FROM : HasId, TO : HasId>(
        val relationType: RelationType<FROM, *, TO>,
        val fromId: String)

data class RelatedToClause<FROM : HasId, TO : HasId>(
        val relationType: RelationType<FROM, *, TO>,
        val toId: String)

data class RelationExpression<FROM : HasId, RELATION : Any, TO : HasId>(
        val relationType: RelationType<FROM, RELATION, TO>,
        val fromId: String,
        val toId: String,
        val relation: RELATION? = null)
