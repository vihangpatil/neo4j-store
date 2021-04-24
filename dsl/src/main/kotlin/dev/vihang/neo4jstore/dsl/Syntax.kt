package dev.vihang.neo4jstore.dsl

import arrow.core.Either
import dev.vihang.neo4jstore.client.ReadTransaction
import dev.vihang.neo4jstore.client.WriteTransaction
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.schema.model.HasId
import dev.vihang.neo4jstore.dsl.model.EntityContext
import dev.vihang.neo4jstore.dsl.model.RelatedFromClause
import dev.vihang.neo4jstore.dsl.model.RelatedToClause
import dev.vihang.neo4jstore.dsl.model.RelationExpression
import dev.vihang.neo4jstore.schema.EntityStore
import dev.vihang.neo4jstore.schema.RelationStore
import dev.vihang.neo4jstore.schema.UniqueRelationStore
import dev.vihang.neo4jstore.schema.entityStore
import dev.vihang.neo4jstore.schema.model.Relation
import dev.vihang.neo4jstore.schema.type
import kotlin.reflect.KProperty1

data class PartialRelationExpression<FROM : HasId, RELATION : Any, TO : HasId>(
    val relation: Relation<FROM, RELATION, TO>,
    val fromId: String,
    val toId: String,
) {

    infix fun using(relationObject: RELATION) = RelationExpression(
        relation = relation,
        fromId = fromId,
        toId = toId,
        dataClass = relationObject
    )
}

fun <E : HasId> ReadTransaction.get(
    entityContext: EntityContext<E>,
): Either<StoreError, E> {
    val entityStore: EntityStore<E> = entityContext.entityClass.entityStore
    return entityStore.get(id = entityContext.id, readTransaction = this)
}

fun <FROM : HasId, TO : HasId> ReadTransaction.get(
    relatedToClause: RelatedToClause<FROM, TO>,
): Either<StoreError, List<FROM>> {
    val entityStore: EntityStore<TO> = relatedToClause.relation.to.entityStore
    return entityStore.getRelatedFrom(
        id = relatedToClause.toId,
        relationType = relatedToClause.relation.type,
        readTransaction = this
    )
}

fun <FROM : HasId, TO : HasId> ReadTransaction.get(
    relatedFromClause: RelatedFromClause<FROM, TO>,
): Either<StoreError, List<TO>> {
    val entityStore: EntityStore<FROM> = relatedFromClause.relation.from.entityStore
    return entityStore.getRelated(
        id = relatedFromClause.fromId,
        relationType = relatedFromClause.relation.type,
        readTransaction = this
    )
}

fun <FROM : HasId, RELATION : Any, TO : HasId> ReadTransaction.get(
    relationExpression: RelationExpression<FROM, RELATION, TO>,
): Either<StoreError, List<RELATION>> {
    return when (val relationStore = relationExpression.relation.type.relationStore) {
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

fun <FROM : HasId, RELATION : Any, TO : HasId> ReadTransaction.get(
    partialRelationExpression: PartialRelationExpression<FROM, RELATION, TO>,
): Either<StoreError, List<RELATION>> =
    get(
        RelationExpression(
            relation = partialRelationExpression.relation,
            fromId = partialRelationExpression.fromId,
            toId = partialRelationExpression.toId
        )
    )


fun <E : HasId> WriteTransaction.create(obj: () -> E): Either<StoreError, Unit> {
    val entity: E = obj()
    val entityStore: EntityStore<E> = entity::class.entityStore as EntityStore<E>
    return entityStore.create(entity = entity, writeTransaction = this)
}

fun <E : HasId> WriteTransaction.update(obj: () -> E): Either<StoreError, Unit> {
    val entity: E = obj()
    val entityStore: EntityStore<E> = entity::class.entityStore as EntityStore<E>
    return entityStore.update(entity = entity, writeTransaction = this)
}

fun <E : HasId> WriteTransaction.update(
    entityContext: EntityContext<E>,
    set: Pair<KProperty1<E, Any?>, String?>,
): Either<StoreError, Unit> {
    val entityStore: EntityStore<E> = entityContext.entityClass.entityStore
    return entityStore.update(
        id = entityContext.id,
        properties = mapOf(set.first.name to set.second),
        writeTransaction = this
    )
}

fun <E : HasId> WriteTransaction.update(
    entityContext: EntityContext<E>,
    vararg set: Pair<KProperty1<E, Any?>, String?>,
): Either<StoreError, Unit> {
    val entityStore: EntityStore<E> = entityContext.entityClass.entityStore
    val properties = set.map { it.first.name to it.second }.toMap()
    return entityStore.update(id = entityContext.id, properties = properties, writeTransaction = this)
}

fun <E : HasId> WriteTransaction.update(
    entityContext: EntityContext<E>,
    set: Map<KProperty1<E, Any?>, String?>,
): Either<StoreError, Unit> {
    val entityStore: EntityStore<E> = entityContext.entityClass.entityStore
    val properties = set.mapKeys { it.key.name }
    return entityStore.update(id = entityContext.id, properties = properties, writeTransaction = this)
}

fun <E : HasId> WriteTransaction.delete(entityContext: EntityContext<E>): Either<StoreError, Unit> {
    val entityStore: EntityStore<E> = entityContext.entityClass.entityStore
    return entityStore.delete(id = entityContext.id, writeTransaction = this)
}

fun <FROM : HasId, RELATION : Any, TO : HasId> WriteTransaction.link(
    expression: () -> RelationExpression<FROM, RELATION, TO>
): Either<StoreError, Unit> {
    val relationExpression = expression()
    val relationStore = relationExpression.relation.type.relationStore
    val relationObject = relationExpression.dataClass
    return when (relationStore) {
        is RelationStore<*, *, *> -> {
            if (relationObject != null) {
                (relationStore as RelationStore<*, RELATION, *>).create(
                    fromId = relationExpression.fromId,
                    toId = relationExpression.toId,
                    relation = relationObject,
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
            if (relationObject != null) {
                (relationStore as UniqueRelationStore<*, RELATION, *>).create(
                    fromId = relationExpression.fromId,
                    toId = relationExpression.toId,
                    relation = relationObject,
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

fun <FROM : HasId, RELATION : Any, TO : HasId> WriteTransaction.unlink(
    expression: () -> RelationExpression<FROM, RELATION, TO>
): Either<StoreError, Unit> {
    val relationExpression = expression()
    val relationStore = relationExpression
        .relation
        .type
        .relationStore
    return relationStore.delete(
        fromId = relationExpression.fromId,
        toId = relationExpression.toId,
        writeTransaction = this
    )
}

fun <FROM : HasId, RELATION : Any, TO : HasId> WriteTransaction.unlink(
    partialRelationExpression: PartialRelationExpression<FROM, RELATION, TO>
): Either<StoreError, Unit> {
    val relationStore = partialRelationExpression
        .relation
        .type
        .relationStore
    return relationStore.delete(
        fromId = partialRelationExpression.fromId,
        toId = partialRelationExpression.toId,
        writeTransaction = this
    )
}