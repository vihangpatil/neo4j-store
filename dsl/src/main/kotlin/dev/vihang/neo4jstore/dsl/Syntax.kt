package dev.vihang.neo4jstore.dsl

import arrow.core.Either
import dev.vihang.neo4jstore.client.ReadTransaction
import dev.vihang.neo4jstore.client.WriteTransaction
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.model.HasId
import dev.vihang.neo4jstore.schema.EntityRegistry
import dev.vihang.neo4jstore.schema.EntityStore
import dev.vihang.neo4jstore.schema.RelationStore
import dev.vihang.neo4jstore.schema.RelationType
import dev.vihang.neo4jstore.schema.UniqueRelationStore
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

open class EntityContext<E : HasId>(val entityClass: KClass<E>, open val id: String)

data class RelatedFromClause<FROM : HasId, TO : HasId>(
        val relationType: RelationType<FROM, *, TO>,
        val fromId: String)

data class RelatedToClause<FROM : HasId, TO : HasId>(
        val relationType: RelationType<FROM, *, TO>,
        val toId: String)

data class RelationExpression<FROM : HasId, RELATION, TO : HasId>(
        val relationType: RelationType<FROM, RELATION, TO>,
        val fromId: String,
        val toId: String,
        val relation: RELATION? = null)

data class PartialRelationExpression<FROM : HasId, RELATION, TO : HasId>(
        val relationType: RelationType<FROM, RELATION, TO>,
        val fromId: String,
        val toId: String,
        val relation: RELATION? = null) {

    infix fun using(relation: RELATION) = RelationExpression(
            relationType = relationType,
            fromId = fromId,
            toId = toId,
            relation = relation)
}

fun <E : HasId> ReadTransaction.get(entityContext: EntityContext<E>): Either<StoreError, E> {
    val entityStore: EntityStore<E> = EntityRegistry.getEntityStore(entityContext.entityClass)
    return entityStore.get(id = entityContext.id, readTransaction = this)
}

fun <FROM : HasId, TO : HasId> ReadTransaction.get(relatedToClause: RelatedToClause<FROM, TO>): Either<StoreError, List<FROM>> {
    val entityStore: EntityStore<TO> = relatedToClause.relationType.to.entityStore
    return entityStore.getRelatedFrom(
            id = relatedToClause.toId,
            relationType = relatedToClause.relationType,
            readTransaction = this)
}

fun <FROM : HasId, TO : HasId> ReadTransaction.get(relatedFromClause: RelatedFromClause<FROM, TO>): Either<StoreError, List<TO>> {
    val entityStore: EntityStore<FROM> = relatedFromClause.relationType.from.entityStore
    return entityStore.getRelated(
            id = relatedFromClause.fromId,
            relationType = relatedFromClause.relationType,
            readTransaction = this)
}

fun <FROM : HasId, RELATION : Any, TO : HasId> ReadTransaction.get(relationExpression: RelationExpression<FROM, RELATION, TO>): Either<StoreError, List<RELATION>> {
    return when (val relationStore = relationExpression.relationType.relationStore) {
        is UniqueRelationStore<*, *, *> -> (relationStore as UniqueRelationStore<FROM, RELATION, TO>).get(
                fromId = relationExpression.fromId,
                toId = relationExpression.toId,
                readTransaction = this
        ).map(::listOf)
        is RelationStore<*, *, *> -> (relationStore as RelationStore<FROM, RELATION, TO>).get(
                fromId = relationExpression.fromId,
                toId = relationExpression.toId,
                readTransaction = this
        )
    }
}

fun <FROM : HasId, RELATION : Any, TO : HasId> ReadTransaction.get(partialRelationExpression: PartialRelationExpression<FROM, RELATION, TO>): Either<StoreError, List<RELATION>> = get(
        RelationExpression(
                relationType = partialRelationExpression.relationType,
                fromId = partialRelationExpression.fromId,
                toId = partialRelationExpression.toId
        )
)


fun <E : HasId> WriteTransaction.create(obj: () -> E): Either<StoreError, Unit> {
    val entity: E = obj()
    val entityStore: EntityStore<E> = EntityRegistry.getEntityStore(entity::class) as EntityStore<E>
    return entityStore.create(entity = entity, writeTransaction = this)
}

fun <E : HasId> WriteTransaction.update(obj: () -> E): Either<StoreError, Unit> {
    val entity: E = obj()
    val entityStore: EntityStore<E> = EntityRegistry.getEntityStore(entity::class) as EntityStore<E>
    return entityStore.update(entity = entity, writeTransaction = this)
}

fun <E : HasId> WriteTransaction.update(entityContext: EntityContext<E>, set: Pair<KProperty1<E, Any?>, String?>): Either<StoreError, Unit> {
    val entityStore: EntityStore<E> = EntityRegistry.getEntityStore(entityContext.entityClass)
    return entityStore.update(id = entityContext.id, properties = mapOf(set.first.name to set.second), writeTransaction = this)
}

fun <E : HasId> WriteTransaction.update(entityContext: EntityContext<E>, vararg set: Pair<KProperty1<E, Any?>, String?>): Either<StoreError, Unit> {
    val entityStore: EntityStore<E> = EntityRegistry.getEntityStore(entityContext.entityClass)
    val properties = set.map { it.first.name to it.second }.toMap()
    return entityStore.update(id = entityContext.id, properties = properties, writeTransaction = this)
}

fun <E : HasId> WriteTransaction.update(entityContext: EntityContext<E>, set: Map<KProperty1<E, Any?>, String?>): Either<StoreError, Unit> {
    val entityStore: EntityStore<E> = EntityRegistry.getEntityStore(entityContext.entityClass)
    val properties = set.mapKeys { it.key.name }
    return entityStore.update(id = entityContext.id, properties = properties, writeTransaction = this)
}

fun <E : HasId> WriteTransaction.delete(entityContext: EntityContext<E>): Either<StoreError, Unit> {
    val entityStore: EntityStore<E> = EntityRegistry.getEntityStore(entityContext.entityClass)
    return entityStore.delete(id = entityContext.id, writeTransaction = this)
}

fun <FROM : HasId, RELATION, TO : HasId> WriteTransaction.link(expression: () -> RelationExpression<FROM, RELATION, TO>): Either<StoreError, Unit> {
    val relationExpression = expression()
    val relationStore = relationExpression.relationType.relationStore
    val relation = relationExpression.relation
    return when (relationStore) {
        is RelationStore<*, *, *> -> {
            if (relation != null) {
                (relationStore as RelationStore<*, RELATION, *>).create(
                        fromId = relationExpression.fromId,
                        toId = relationExpression.toId,
                        relation = relation,
                        writeTransaction = this
                )
            } else {
                relationStore.create(
                        fromId = relationExpression.fromId,
                        toId = relationExpression.toId,
                        writeTransaction = this
                )
            }
        }
        is UniqueRelationStore<*, *, *> -> {
            if (relation != null) {
                (relationStore as UniqueRelationStore<*, RELATION, *>).create(
                        fromId = relationExpression.fromId,
                        toId = relationExpression.toId,
                        relation = relation,
                        writeTransaction = this
                )
            } else {
                relationStore.createIfAbsent(
                        fromId = relationExpression.fromId,
                        toId = relationExpression.toId,
                        writeTransaction = this
                )
            }
        }
    }
}

fun <FROM : HasId, RELATION, TO : HasId> WriteTransaction.unlink(expression: () -> RelationExpression<FROM, RELATION, TO>): Either<StoreError, Unit> {
    val relationExpression = expression()
    val relationStore = relationExpression
            .relationType
            .relationStore
    return relationStore.delete(
            fromId = relationExpression.fromId,
            toId = relationExpression.toId,
            writeTransaction = this
    )
}

fun <FROM : HasId, RELATION, TO : HasId> WriteTransaction.unlink(partialRelationExpression: PartialRelationExpression<FROM, RELATION, TO>): Either<StoreError, Unit> {
    val relationStore = partialRelationExpression
            .relationType
            .relationStore
    return relationStore.delete(
            fromId = partialRelationExpression.fromId,
            toId = partialRelationExpression.toId,
            writeTransaction = this
    )
}