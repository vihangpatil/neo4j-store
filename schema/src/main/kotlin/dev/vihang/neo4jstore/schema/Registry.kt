package dev.vihang.neo4jstore.schema

import dev.vihang.neo4jstore.schema.model.HasId
import dev.vihang.neo4jstore.schema.model.Relation
import kotlin.reflect.KClass

private object EntityRegistry {

    private val entityTypeMap = mutableMapOf<KClass<out HasId>, EntityType<out HasId>>()
    private val entityStoreMap = mutableMapOf<KClass<out HasId>, EntityStore<out HasId>>()

    fun <E : HasId> getEntityType(kClass: KClass<E>): EntityType<E> {
        return entityTypeMap.getOrPut(kClass) {
            val entityType = EntityType(dataClass = kClass)
            entityStoreMap.getOrPut(kClass) {
                EntityStore(entityType = entityType)
            }
            entityType
        } as EntityType<E>
    }

    fun <E : HasId> getEntityStore(kClass: KClass<E>): EntityStore<E> {
        return entityStoreMap.getOrPut(kClass) {
            val entityType = entityTypeMap.getOrPut(kClass) {
                EntityType(dataClass = kClass)
            }
            EntityStore(entityType = entityType)
        } as EntityStore<E>
    }
}

val <E : HasId> KClass<E>.entityType: EntityType<E>
    get() = EntityRegistry.getEntityType(this)

val <E : HasId> KClass<E>.entityStore: EntityStore<E>
    get() = EntityRegistry.getEntityStore(this)

private object RelationRegistry {

    private val relationTypeMap = mutableMapOf<Relation<out HasId, *, out HasId>, RelationType<out HasId, *, out HasId>>()

    fun <FROM : HasId, RELATION : Any, TO : HasId> getRelationType(relation: Relation<FROM, RELATION, TO>): RelationType<FROM, RELATION, TO> {
        return relationTypeMap.getOrPut(relation) {
            RelationType(relation)
        } as RelationType<FROM, RELATION, TO>
    }
}

val <FROM : HasId, RELATION : Any, TO : HasId> Relation<FROM, RELATION, TO>.type: RelationType<FROM, RELATION, TO>
    get() = RelationRegistry.getRelationType(this)