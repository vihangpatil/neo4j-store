package dev.vihang.neo4jstore.schema

import dev.vihang.neo4jstore.schema.model.HasId
import dev.vihang.neo4jstore.schema.model.Relation
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
    get() = EntityRegistry.getEntityType(this)

val <E : HasId> KClass<E>.entityStore: EntityStore<E>
    get() = EntityRegistry.getEntityStore(this)

object RelationRegistry {

    private val relationTypeMap = mutableMapOf<Relation<out HasId, *, out HasId>, RelationType<out HasId, *, out HasId>>()

    fun <FROM : HasId, RELATION : Any, TO : HasId> getRelationType(relation: Relation<FROM, RELATION, TO>): RelationType<FROM, RELATION, TO> {
        return (relationTypeMap as MutableMap<Relation<FROM, RELATION, TO>, RelationType<FROM, RELATION, TO>>)
                .getOrPut(relation) {
                    RelationType(relation)
                }
    }
}

val <FROM : HasId, RELATION : Any, TO : HasId> Relation<FROM, RELATION, TO>.type : RelationType<FROM, RELATION, TO>
    get() = RelationRegistry.getRelationType(this)