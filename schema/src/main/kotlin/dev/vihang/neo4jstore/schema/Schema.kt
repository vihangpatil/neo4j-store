package dev.vihang.neo4jstore.schema

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.type.TypeReference
import dev.vihang.common.jsonmapper.objectMapper
import dev.vihang.common.logging.getLogger
import dev.vihang.neo4jstore.error.AlreadyExistsError
import dev.vihang.neo4jstore.error.NotCreatedError
import dev.vihang.neo4jstore.error.NotDeletedError
import dev.vihang.neo4jstore.error.NotFoundError
import dev.vihang.neo4jstore.error.NotUpdatedError
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.model.HasId
import dev.vihang.neo4jstore.schema.Graph.read
import dev.vihang.neo4jstore.schema.Graph.write
import dev.vihang.neo4jstore.schema.ObjectHandler.getProperties
import dev.vihang.neo4jstore.schema.ObjectHandler.getStringProperties
import org.neo4j.driver.Result
import org.neo4j.driver.Transaction
import kotlin.reflect.KClass


data class EntityType<ENTITY : HasId>(
        private val dataClass: Class<ENTITY>,
        val name: String = dataClass.simpleName) {

    var entityStore: EntityStore<ENTITY> = EntityStore(this)

    fun createEntity(map: Map<String, Any>): ENTITY = ObjectHandler.getObject(map, dataClass)
}

class Relation(
        val name: String,
        val from: KClass<out HasId>,
        val to: KClass<out HasId>,
        val isUnique: Boolean = true)

data class RelationType<FROM : HasId, RELATION, TO : HasId>(
        private val relation: Relation,
        val from: EntityType<FROM>,
        val to: EntityType<TO>,
        private val dataClass: Class<RELATION>) {

    val relationStore: BaseRelationStore = if (relation.isUnique) {
        UniqueRelationStore(this)
    } else {
        RelationStore(this)
    }

    val name: String = relation.name

    fun createRelation(map: Map<String, Any>): RELATION {
        return ObjectHandler.getObject(map, dataClass)
    }
}

class EntityStore<E : HasId>(private val entityType: EntityType<E>) {

    init {
        entityType.entityStore = this
    }

    fun get(id: String, transaction: Transaction): Either<StoreError, E> {
        return read("""MATCH (node:${entityType.name} {id: '$id'}) RETURN node;""",
                transaction) { result ->
            if (result.hasNext())
                Either.right(entityType.createEntity(result.single().get("node").asMap()))
            else
                Either.left(NotFoundError(type = entityType.name, id = id))
        }
    }

    fun create(entity: E, transaction: Transaction): Either<StoreError, Unit> {

        return doNotExist(id = entity.id, transaction = transaction).flatMap {

            val properties = getStringProperties(entity).toMutableMap()
            properties.putIfAbsent("id", entity.id)
            val parameters: Map<String, Any> = mapOf("props" to properties)
            write(query = """CREATE (node:${entityType.name} ${'$'}props);""",
                    parameters = parameters,
                    transaction= transaction) {
                if (it.consume().counters().nodesCreated() == 1)
                    Unit.right()
                else
                    Either.left(NotCreatedError(type = entityType.name, id = entity.id))
            }
        }
    }

    fun <TO : HasId> getRelated(
            id: String,
            relationType: RelationType<E, *, TO>,
            transaction: Transaction): Either<StoreError, List<TO>> {

        return exists(id, transaction).flatMap {

            read("""
                MATCH (:${relationType.from.name} {id: '$id'})-[:${relationType.name}]->(node:${relationType.to.name})
                RETURN node;
                """.trimIndent(),
                    transaction) { result ->
                Either.right(
                        result.list { record -> relationType.to.createEntity(record["node"].asMap()) })
            }
        }
    }

    fun <FROM : HasId> getRelatedFrom(
            id: String,
            relationType: RelationType<FROM, *, E>,
            transaction: Transaction): Either<StoreError, List<FROM>> {

        return exists(id, transaction).flatMap {

            read("""
                MATCH (node:${relationType.from.name})-[:${relationType.name}]->(:${relationType.to.name} {id: '$id'})
                RETURN node;
                """.trimIndent(),
                    transaction) { result ->
                Either.right(
                        result.list { record -> relationType.from.createEntity(record["node"].asMap()) })
            }
        }
    }

    fun update(entity: E, transaction: Transaction): Either<StoreError, Unit> {

        return exists(entity.id, transaction).flatMap {
            val properties = getStringProperties(entity).toMutableMap()
            properties.putIfAbsent("id", entity.id)
            val parameters: Map<String, Any> = mapOf("props" to properties)
            write(query = """MATCH (node:${entityType.name} { id: '${entity.id}' }) SET node = ${'$'}props ;""",
                    parameters = parameters,
                    transaction = transaction) { result ->
                Either.cond(
                        test = result.consume().counters().containsUpdates(), // TODO vihang: this is not perfect way to check if updates are applied
                        ifTrue = {},
                        ifFalse = { NotUpdatedError(type = entityType.name, id = entity.id) })
            }
        }
    }

    fun update(id: String, properties: Map<String, Any?>, transaction: Transaction): Either<StoreError, Unit> {

        val props = properties
                .filterValues { it != null }
                .mapValues { it.value.toString() }
        val parameters: Map<String, Any> = mapOf("props" to props)
        return exists(id, transaction).flatMap {
            write(query = """MATCH (node:${entityType.name} { id: '$id' }) SET node += ${'$'}props ;""",
                    parameters = parameters,
                    transaction = transaction) { result ->
                Either.cond(
                        test = result.consume().counters().containsUpdates(), // TODO vihang: this is not perfect way to check if updates are applied
                        ifTrue = {},
                        ifFalse = { NotUpdatedError(type = entityType.name, id = id) })
            }
        }
    }

    fun delete(id: String, transaction: Transaction): Either<StoreError, Unit> =
            exists(id, transaction).flatMap {
                write("""MATCH (node:${entityType.name} {id: '$id'} ) DETACH DELETE node;""",
                        transaction) { result ->
                    Either.cond(
                            test = result.consume().counters().nodesDeleted() == 1,
                            ifTrue = {},
                            ifFalse = { NotDeletedError(type = entityType.name, id = id) })
                }
            }

    fun exists(id: String, transaction: Transaction): Either<StoreError, Unit> =
            read("""MATCH (node:${entityType.name} {id: '$id'} ) RETURN count(node);""",
                    transaction) { result ->
                Either.cond(
                        test = result.single()["count(node)"].asInt(0) == 1,
                        ifTrue = {},
                        ifFalse = { NotFoundError(type = entityType.name, id = id) })
            }

    private fun doNotExist(id: String, transaction: Transaction): Either<StoreError, Unit> =
            read("""MATCH (node:${entityType.name} {id: '$id'} ) RETURN count(node);""",
                    transaction) { result ->
                Either.cond(
                        test = result.single()["count(node)"].asInt(1) == 0,
                        ifTrue = {},
                        ifFalse = { AlreadyExistsError(type = entityType.name, id = id) })
            }
}

sealed class BaseRelationStore {
    abstract fun delete(fromId: String, toId: String, transaction: Transaction): Either<StoreError, Unit>
}

// TODO vihang: check if relation already exists, with allow duplicate boolean flag param
class RelationStore<FROM : HasId, RELATION, TO : HasId>(private val relationType: RelationType<FROM, RELATION, TO>) : BaseRelationStore() {

    fun create(from: FROM, relation: RELATION, to: TO, transaction: Transaction): Either<StoreError, Unit> {

        val properties = getStringProperties(relation as Any)
        val parameters: Map<String, Any> = mapOf("props" to properties)
        return write( query = """
                    MATCH (from:${relationType.from.name} { id: '${from.id}' }),(to:${relationType.to.name} { id: '${to.id}' })
                    CREATE (from)-[:${relationType.name} ${'$'}props ]->(to);
                    """.trimIndent(),
                parameters = parameters,
                transaction = transaction) { result ->

            // TODO vihang: validate if 'from' and 'to' node exists
            Either.cond(
                    test = result.consume().counters().relationshipsCreated() == 1,
                    ifTrue = {},
                    ifFalse = { NotCreatedError(type = relationType.name, id = "${from.id} -> ${to.id}") })
        }
    }

    fun create(from: FROM, to: TO, transaction: Transaction): Either<StoreError, Unit> = write("""
                MATCH (from:${relationType.from.name} { id: '${from.id}' }),(to:${relationType.to.name} { id: '${to.id}' })
                CREATE (from)-[:${relationType.name}]->(to);
                """.trimIndent(),
            transaction) { result ->

        // TODO vihang: validate if 'from' and 'to' node exists
        Either.cond(
                test = result.consume().counters().relationshipsCreated() == 1,
                ifTrue = {},
                ifFalse = { NotCreatedError(type = relationType.name, id = "${from.id} -> ${to.id}") })
    }

    fun create(fromId: String, toId: String, transaction: Transaction): Either<StoreError, Unit> = write("""
                MATCH (from:${relationType.from.name} { id: '$fromId' }),(to:${relationType.to.name} { id: '$toId' })
                CREATE (from)-[:${relationType.name}]->(to);
                """.trimIndent(),
            transaction) { result ->

        // TODO vihang: validate if 'from' and 'to' node exists
        Either.cond(
                test = result.consume().counters().relationshipsCreated() == 1,
                ifTrue = {},
                ifFalse = { NotCreatedError(type = relationType.name, id = "$fromId -> $toId") })
    }

    fun create(fromId: String, relation: RELATION, toId: String, transaction: Transaction): Either<StoreError, Unit> {

        val properties = getStringProperties(relation as Any)
        val parameters: Map<String, Any> = mapOf("props" to properties)
        return write(query = """
                MATCH (from:${relationType.from.name} { id: '$fromId' }),(to:${relationType.to.name} { id: '$toId' })
                CREATE (from)-[:${relationType.name} ${'$'}props ]->(to);
                """.trimIndent(),
                parameters = parameters,
                transaction = transaction) { result ->

            // TODO vihang: validate if 'from' and 'to' node exists
            Either.cond(
                    test = result.consume().counters().relationshipsCreated() == 1,
                    ifTrue = {},
                    ifFalse = { NotCreatedError(type = relationType.name, id = "$fromId -> $toId") })
        }
    }

    // TODO vihang: use parameters
    fun create(fromId: String, toIds: Collection<String>, transaction: Transaction): Either<StoreError, Unit> = write("""
                MATCH (to:${relationType.to.name})
                WHERE to.id in [${toIds.joinToString(",") { "'$it'" }}]
                WITH to
                MATCH (from:${relationType.from.name} { id: '$fromId' })
                CREATE (from)-[:${relationType.name}]->(to);
                """.trimIndent(),
            transaction) { result ->
        // TODO vihang: validate if 'from' and 'to' node exists
        val actualCount = result.consume().counters().relationshipsCreated()
        Either.cond(
                test = actualCount == toIds.size,
                ifTrue = {},
                ifFalse = {
                    NotCreatedError(
                            type = relationType.name,
                            expectedCount = toIds.size,
                            actualCount = actualCount)
                })
    }

    // TODO vihang: use parameters
    fun create(fromIds: Collection<String>, toId: String, transaction: Transaction): Either<StoreError, Unit> = write("""
                MATCH (from:${relationType.from.name})
                WHERE from.id in [${fromIds.joinToString(",") { "'$it'" }}]
                WITH from
                MATCH (to:${relationType.to.name} { id: '$toId' })
                CREATE (from)-[:${relationType.name}]->(to);
                """.trimIndent(),
            transaction) { result ->

        // TODO vihang: validate if 'from' and 'to' node exists
        val actualCount = result.consume().counters().relationshipsCreated()
        Either.cond(
                test = actualCount == fromIds.size,
                ifTrue = {},
                ifFalse = {
                    NotCreatedError(
                            type = relationType.name,
                            expectedCount = fromIds.size,
                            actualCount = actualCount)
                })
    }

    fun get(
            fromId: String,
            toId: String,
            transaction: Transaction): Either<StoreError, List<RELATION>> {

        return relationType.from.entityStore.exists(fromId, transaction)
                .flatMap { relationType.to.entityStore.exists(fromId, transaction) }
                .flatMap {
                    read("""
                MATCH (:${relationType.from.name} { id: '$fromId' })-[r:${relationType.name}]-(:${relationType.to.name} { id: '$toId' })
                return r;
                """.trimIndent(),
                            transaction) { result ->
                        result.list { record -> relationType.createRelation(record["r"].asMap()) }
                                .right()
                    }
                }
    }

    fun removeAll(toId: String, transaction: Transaction): Either<StoreError, Unit> = write("""
                MATCH (from:${relationType.from.name})-[r:${relationType.name}]->(to:${relationType.to.name} { id: '$toId' })
                DELETE r;
                """.trimIndent(),
            transaction) {
        // TODO vihang: validate if 'to' node exists
        Unit.right()
    }

    override fun delete(fromId: String, toId: String, transaction: Transaction): Either<StoreError, Unit> = write("""
                MATCH (:${relationType.from.name} { id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
                DELETE r
                """.trimMargin(),
            transaction) { result ->

        Either.cond(result.consume().counters().relationshipsDeleted() > 0,
                ifTrue = { Unit },
                ifFalse = { NotDeletedError(relationType.name, "$fromId -> $toId") })
    }
}

class UniqueRelationStore<FROM : HasId, RELATION, TO : HasId>(private val relationType: RelationType<FROM, RELATION, TO>) : BaseRelationStore() {

    // If relation does not exists, then it creates new relation.
    fun createIfAbsent(fromId: String, toId: String, transaction: Transaction): Either<StoreError, Unit> {

        return relationType.from.entityStore.exists(fromId, transaction)
                .flatMap {
                    relationType.to.entityStore.exists(toId, transaction)
                }.flatMap {

                    doNotExist(fromId, toId, transaction).fold(
                            { Unit.right() },
                            {
                                write("""
                                    MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                                    MERGE (fromId)-[:${relationType.name}]->(toId)
                                    """.trimMargin(),
                                        transaction) { result ->

                                    Either.cond(result.consume().counters().relationshipsCreated() == 1,
                                            ifTrue = { Unit },
                                            ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
                                }
                            })
                }
    }

    // If relation does not exists, then it creates new relation.
    fun createIfAbsent(fromId: String, relation: RELATION, toId: String, transaction: Transaction): Either<StoreError, Unit> {

        return relationType.from.entityStore.exists(fromId, transaction)
                .flatMap {
                    relationType.to.entityStore.exists(toId, transaction)
                }.flatMap {

                    doNotExist(fromId, toId, transaction).fold(
                            { Unit.right() },
                            {
                                val properties = getProperties(relation as Any)
                                // TODO vihang: set props using parameter
                                val strProps: String = properties.entries.joinToString(",") { """`${it.key}`: "${it.value}"""" }

                                write("""
                                    MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                                    MERGE (fromId)-[:${relationType.name} { $strProps } ]->(toId)
                                    """.trimMargin(),
                                        transaction) { result ->

                                    Either.cond(result.consume().counters().relationshipsCreated() == 1,
                                            ifTrue = { Unit },
                                            ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
                                }
                            })
                }
    }

    // If relation does not exists, then it creates new relation. Or else updates it.
    fun createOrUpdate(fromId: String, relation: RELATION, toId: String, transaction: Transaction): Either<StoreError, Unit> {

        return relationType.from.entityStore.exists(fromId, transaction)
                .flatMap {
                    relationType.to.entityStore.exists(toId, transaction)
                }.flatMap {

                    val properties = getProperties(relation as Any)
                    doNotExist(fromId, toId, transaction).fold(
                            {
                                // TODO vihang: set props using parameter
                                val setClause: String = properties.entries.fold("") { acc, entry -> """$acc SET r.`${entry.key}` = '${entry.value}' """ }
                                write(
                                        """MATCH (fromId:${relationType.from.name} {id: '$fromId'})-[r:${relationType.name}]->(toId:${relationType.to.name} {id: '$toId'})
                                    $setClause ;""".trimMargin(),
                                        transaction) { result ->
                                    Either.cond(
                                            test = result.consume().counters().containsUpdates(), // TODO vihang: this is not perfect way to check if updates are applied
                                            ifTrue = {},
                                            ifFalse = { NotUpdatedError(type = relationType.name, id = "$fromId -> $toId") })
                                }
                            },
                            {
                                // TODO vihang: set props using parameter
                                val strProps: String = properties.entries.joinToString(",") { """`${it.key}`: "${it.value}"""" }

                                write("""
                                MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                                MERGE (fromId)-[:${relationType.name} { $strProps } ]->(toId)
                                """.trimMargin(),
                                        transaction) { result ->

                                    Either.cond(result.consume().counters().relationshipsCreated() == 1,
                                            ifTrue = { Unit },
                                            ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
                                }
                            }
                    )
                }
    }

    // If relation exists, then it fails with Already Exists Error, else it creates new relation.
    fun create(fromId: String, toId: String, transaction: Transaction): Either<StoreError, Unit> {

        return doNotExist(fromId, toId, transaction).flatMap {
            write("""
                        MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                        MERGE (fromId)-[:${relationType.name}]->(toId)
                        """.trimMargin(),
                    transaction) { result ->

                Either.cond(result.consume().counters().relationshipsCreated() == 1,
                        ifTrue = { Unit },
                        ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
            }
        }
    }

    // If relation exists, then it fails with Already Exists Error, else it creates new relation.
    fun create(fromId: String, relation: RELATION, toId: String, transaction: Transaction): Either<StoreError, Unit> {

        return doNotExist(fromId, toId, transaction).flatMap {
            val properties = getProperties(relation as Any)
            // TODO vihang: set props using parameter
            val strProps: String = properties.entries.joinToString(",") { """`${it.key}`: "${it.value}"""" }

            write("""
                        MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                        MERGE (fromId)-[:${relationType.name}  { $strProps } ]->(toId)
                        """.trimMargin(),
                    transaction) { result ->

                Either.cond(result.consume().counters().relationshipsCreated() == 1,
                        ifTrue = { Unit },
                        ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
            }
        }
    }

    fun get(fromId: String, toId: String, transaction: Transaction): Either<StoreError, RELATION> = read("""
                MATCH (:${relationType.from.name} {id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
                RETURN r
                """.trimMargin(),
            transaction) { result ->

        Either.cond(result.hasNext(),
                ifTrue = { relationType.createRelation(result.single()["r"].asMap()) },
                ifFalse = { NotFoundError(relationType.name, "$fromId -> $toId") })
                .flatMap {
                    relation -> relation?.right() ?: NotFoundError(relationType.name, "$fromId -> $toId").left()
                }
    }

    override fun delete(fromId: String, toId: String, transaction: Transaction): Either<StoreError, Unit> = write("""
                MATCH (:${relationType.from.name} { id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
                DELETE r
                """.trimMargin(),
            transaction) { result ->

        Either.cond(result.consume().counters().relationshipsDeleted() == 1,
                ifTrue = { Unit },
                ifFalse = { NotDeletedError(relationType.name, "$fromId -> $toId") })
    }

    private fun doNotExist(fromId: String, toId: String, transaction: Transaction): Either<StoreError, Unit> = read("""
                MATCH (:${relationType.from.name} {id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
                RETURN count(r)
                """.trimMargin(),
            transaction) { result ->

        Either.cond(
                test = result.single()["count(r)"].asInt(1) == 0,
                ifTrue = {},
                ifFalse = { AlreadyExistsError(type = relationType.name, id = "$fromId -> $toId") })

    }
}

//
// Helper wrapping Neo4j Client
//
object Graph {

    private val LOG by getLogger()

    fun <R> write(query: String, transaction: Transaction, parameters: Map<String, Any> = emptyMap(), transform: (Result) -> R): R {
        LOG.trace("write:[\n$query\n]")
        return transaction.run(query, parameters).let(transform)
    }

    fun <R> read(query: String, transaction: Transaction, parameters: Map<String, Any> = emptyMap(), transform: (Result) -> R): R {
        LOG.trace("read:[\n$query\n]")
        return transaction.run(query, parameters).let(transform)
    }
}

//
// Object mapping functions
//
object ObjectHandler {

    private const val SEPARATOR = '/'

    //
    // Object to Map
    //
    fun getStringProperties(any: Any): Map<String, String> = getProperties(any).mapValues { it.value.toString() }

    fun getProperties(any: Any): Map<String, Any> = toSimpleMap(
            objectMapper.convertValue(any, object : TypeReference<Map<String, Any?>>() {}))

    private fun toSimpleMap(map: Map<String, Any?>, prefix: String = ""): Map<String, Any> {
        val outputMap: MutableMap<String, Any> = LinkedHashMap()
        map.forEach { (key, value) ->
            when (value) {
                is Map<*, *> -> outputMap.putAll(toSimpleMap(value as Map<String, Any>, "$prefix$key$SEPARATOR"))
                is List<*> -> println("Skipping list value: $value for key: $key")
                null -> Unit
                else -> outputMap["$prefix$key"] = value
            }
        }
        return outputMap
    }

    //
    // Map to Object
    //

    fun <D> getObject(map: Map<String, Any>, dataClass: Class<D>): D {
        return objectMapper.convertValue(toNestedMap(map), dataClass)
    }

    internal fun toNestedMap(map: Map<String, Any>): Map<String, Any> {
        val outputMap: MutableMap<String, Any> = LinkedHashMap()
        map.forEach { (key, value) ->
            if (key.contains(SEPARATOR)) {
                val keys = key.split(SEPARATOR)
                var loopMap = outputMap
                repeat(keys.size - 1) { i ->
                    loopMap.putIfAbsent(keys[i], LinkedHashMap<String, Any>())
                    loopMap = loopMap[keys[i]] as MutableMap<String, Any>
                }
                loopMap[keys.last()] = value

            } else {
                outputMap[key] = value
            }
        }
        return outputMap
    }
}

// Need a dummy Void class with no-arg constructor to represent Relations with no properties.
class None