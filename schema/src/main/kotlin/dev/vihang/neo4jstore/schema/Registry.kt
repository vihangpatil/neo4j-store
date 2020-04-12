package dev.vihang.neo4jstore.schema

import dev.vihang.neo4jstore.model.HasId
import dev.vihang.neo4jstore.schema.EntityRegistry.getEntityStore
import dev.vihang.neo4jstore.schema.EntityRegistry.getEntityType
import kotlin.reflect.KClass

object EntityRegistry {

    private val entityTypeMap = mutableMapOf<KClass<out HasId>, EntityType<out HasId>>()
    private val entityStoreMap = mutableMapOf<KClass<out HasId>, EntityStore<out HasId>>()

    fun <E : HasId> getEntityType(kClass: KClass<E>): EntityType<E> {
        return (entityTypeMap as MutableMap<KClass<E>, EntityType<E>>)
                .getOrPut(kClass) {
                    EntityType(dataClass = kClass)
                }
    }

    fun <E : HasId> getEntityStore(kClass: KClass<E>): EntityStore<E> {
        val entityType = getEntityType(kClass)
        return (entityStoreMap as MutableMap<KClass<E>, EntityStore<E>>).getOrPut(kClass) {
            EntityStore(entityClass = kClass, entityType = entityType)
        }
    }
}

val <E : HasId> KClass<E>.entityType: EntityType<E>
    get() = getEntityType(this)

val <E : HasId> KClass<E>.entityStore: EntityStore<E>
    get() = getEntityStore(this)
