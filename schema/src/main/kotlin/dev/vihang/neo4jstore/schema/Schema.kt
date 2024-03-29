package dev.vihang.neo4jstore.schema

import arrow.core.Either
import arrow.core.continuations.either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.core.type.TypeReference
import dev.vihang.common.jsonmapper.objectMapper
import dev.vihang.neo4jstore.client.ReadAsyncTransaction
import dev.vihang.neo4jstore.client.ReadTransaction
import dev.vihang.neo4jstore.client.WriteAsyncTransaction
import dev.vihang.neo4jstore.client.WriteTransaction
import dev.vihang.neo4jstore.client.read
import dev.vihang.neo4jstore.client.write
import dev.vihang.neo4jstore.error.AlreadyExistsError
import dev.vihang.neo4jstore.error.NotCreatedError
import dev.vihang.neo4jstore.error.NotDeletedError
import dev.vihang.neo4jstore.error.NotFoundError
import dev.vihang.neo4jstore.error.NotUpdatedError
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.schema.ObjectHandler.getProperties
import dev.vihang.neo4jstore.schema.ObjectHandler.getStringProperties
import dev.vihang.neo4jstore.schema.model.HasId
import dev.vihang.neo4jstore.schema.model.Relation
import kotlinx.coroutines.future.await
import kotlin.reflect.KClass

data class EntityType<ENTITY : HasId>(
    val dataClass: KClass<ENTITY>,
    val name: String = dataClass.java.simpleName
) {

    lateinit var entityStore: EntityStore<ENTITY>

    fun createEntity(map: Map<String, Any>): ENTITY = ObjectHandler.getObject(map, dataClass.java)
}

data class RelationType<FROM : HasId, RELATION : Any, TO : HasId>(
    val relation: Relation<FROM, RELATION, TO>
) {

    val from: EntityType<FROM> = relation.from.entityType

    val to: EntityType<TO> = relation.to.entityType

    val relationStore: BaseRelationStore = if (relation.isUnique) {
        UniqueRelationStore(this)
    } else {
        RelationStore(this)
    }

    val name: String = relation.name

    fun createRelation(map: Map<String, Any>): RELATION {
        return ObjectHandler.getObject(map, relation.relation.java)
    }
}

class EntityStore<E : HasId>(val entityType: EntityType<E>) {

    init {
        entityType.entityStore = this
    }

    fun get(id: String, readTransaction: ReadTransaction): Either<StoreError, E> {
        return readTransaction.read("""MATCH (node:${entityType.name} {id: '$id'}) RETURN node;""")
        { result ->
            if (result.hasNext())
                entityType.createEntity(result.single().get("node").asMap()).right()
            else
                NotFoundError(type = entityType.name, id = id).left()
        }
    }

    suspend fun get(id: String, readAsyncTransaction: ReadAsyncTransaction): Either<StoreError, E> {
        return readAsyncTransaction.read("""MATCH (node:${entityType.name} {id: '$id'}) RETURN node;""")
        { resultCursor ->
            val records = resultCursor.listAsync().await()
            if (records.isNotEmpty())
                entityType.createEntity(records.single().get("node").asMap()).right()
            else
                NotFoundError(type = entityType.name, id = id).left()
        }
    }

    fun create(entity: E, writeTransaction: WriteTransaction): Either<StoreError, Unit> {

        return doNotExist(entity.id, writeTransaction).flatMap {

            val properties = getStringProperties(entity).toMutableMap()
            properties.putIfAbsent("id", entity.id)
            val parameters: Map<String, Any> = mapOf("props" to properties)
            writeTransaction.write(
                query = """CREATE (node:${entityType.name} ${'$'}props);""",
                parameters = parameters
            ) {
                if (it.consume().counters().nodesCreated() == 1)
                    Unit.right()
                else
                    NotCreatedError(type = entityType.name, id = entity.id).left()
            }
        }
    }

    suspend fun create(entity: E, writeAsyncTransaction: WriteAsyncTransaction): Either<StoreError, Unit> {

        return either {
            doNotExist(entity.id, writeAsyncTransaction).bind()
            val properties = getStringProperties(entity).toMutableMap()
            properties.putIfAbsent("id", entity.id)
            val parameters: Map<String, Any> = mapOf("props" to properties)
            writeAsyncTransaction.write(
                query = """CREATE (node:${entityType.name} ${'$'}props);""",
                parameters = parameters
            ) {
                if (it.consumeAsync().await().counters().nodesCreated() == 1)
                    Unit.right()
                else
                    NotCreatedError(type = entityType.name, id = entity.id).left()
            }
        }
    }

    fun <TO : HasId> getRelated(
        id: String,
        relationType: RelationType<E, *, TO>,
        readTransaction: ReadTransaction
    ): Either<StoreError, List<TO>> {

        return exists(id, readTransaction).flatMap {

            readTransaction.read(
                """
                MATCH (:${relationType.from.name} {id: '$id'})-[:${relationType.name}]->(node:${relationType.to.name})
                RETURN node;
                """.trimIndent()
            ) { result ->
                result.list { record -> relationType.to.createEntity(record["node"].asMap()) }.right()
            }
        }
    }

    suspend fun <TO : HasId> getRelated(
        id: String,
        relationType: RelationType<E, *, TO>,
        readAsyncTransaction: ReadAsyncTransaction
    ): Either<StoreError, List<TO>> {

        return either {
            exists(id, readAsyncTransaction).bind()

            readAsyncTransaction.read(
                """
            MATCH (:${relationType.from.name} {id: '$id'})-[:${relationType.name}]->(node:${relationType.to.name})
            RETURN node;
            """.trimIndent()
            ) { resultCursor ->
                resultCursor.listAsync().await()
                    .map { record -> relationType.to.createEntity(record["node"].asMap()) }.right()
            }.bind()
        }
    }

    fun <FROM : HasId> getRelatedFrom(
        id: String,
        relationType: RelationType<FROM, *, E>,
        readTransaction: ReadTransaction
    ): Either<StoreError, List<FROM>> {

        return exists(id, readTransaction).flatMap {

            readTransaction.read(
                """
                MATCH (node:${relationType.from.name})-[:${relationType.name}]->(:${relationType.to.name} {id: '$id'})
                RETURN node;
                """.trimIndent()
            ) { result ->
                result.list { record -> relationType.from.createEntity(record["node"].asMap()) }.right()
            }
        }
    }

    suspend fun <FROM : HasId> getRelatedFrom(
        id: String,
        relationType: RelationType<FROM, *, E>,
        readAsyncTransaction: ReadAsyncTransaction
    ): Either<StoreError, List<FROM>> {

        return either {
            exists(id, readAsyncTransaction).bind()

            readAsyncTransaction.read(
                """
            MATCH (node:${relationType.from.name})-[:${relationType.name}]->(:${relationType.to.name} {id: '$id'})
            RETURN node;
            """.trimIndent()
            ) { resultCursor ->
                resultCursor.listAsync().await()
                    .map { record -> relationType.from.createEntity(record["node"].asMap()) }.right()
            }.bind()
        }
    }

    fun update(entity: E, writeTransaction: WriteTransaction): Either<StoreError, Unit> {

        return exists(entity.id, writeTransaction).flatMap {
            val properties = getStringProperties(entity).toMutableMap()
            properties.putIfAbsent("id", entity.id)
            val parameters: Map<String, Any> = mapOf("props" to properties)
            writeTransaction.write(
                query = """MATCH (node:${entityType.name} { id: '${entity.id}' }) SET node = ${'$'}props ;""",
                parameters = parameters
            ) { result ->
                Either.conditionally(
                    test = result.consume().counters()
                        .containsUpdates(), // TODO vihang: this is not perfect way to check if updates are applied
                    ifTrue = {},
                    ifFalse = { NotUpdatedError(type = entityType.name, id = entity.id) })
            }
        }
    }

    fun update(
        id: String,
        properties: Map<String, Any?>,
        writeTransaction: WriteTransaction
    ): Either<StoreError, Unit> {

        val props = properties
            .filterValues { it != null }
            .mapValues { it.value.toString() }
        val parameters: Map<String, Any> = mapOf("props" to props)
        return exists(id, writeTransaction).flatMap {
            writeTransaction.write(
                query = """MATCH (node:${entityType.name} { id: '$id' }) SET node += ${'$'}props ;""",
                parameters = parameters
            ) { result ->
                Either.conditionally(
                    test = result.consume().counters()
                        .containsUpdates(), // TODO vihang: this is not perfect way to check if updates are applied
                    ifTrue = {},
                    ifFalse = { NotUpdatedError(type = entityType.name, id = id) })
            }
        }
    }

    fun delete(id: String, writeTransaction: WriteTransaction): Either<StoreError, Unit> =
        exists(id, writeTransaction).flatMap {
            writeTransaction.write("""MATCH (node:${entityType.name} {id: '$id'} ) DETACH DELETE node;""")
            { result ->
                Either.conditionally(
                    test = result.consume().counters().nodesDeleted() == 1,
                    ifTrue = {},
                    ifFalse = { NotDeletedError(type = entityType.name, id = id) })
            }
        }

    fun exists(id: String, readTransaction: ReadTransaction): Either<StoreError, Unit> =
        readTransaction.read("""MATCH (node:${entityType.name} {id: '$id'} ) RETURN count(node);""")
        { result ->
            Either.conditionally(
                test = result.single()["count(node)"].asInt(0) == 1,
                ifTrue = {},
                ifFalse = { NotFoundError(type = entityType.name, id = id) })
        }

    suspend fun exists(id: String, readAsyncTransaction: ReadAsyncTransaction): Either<StoreError, Unit> =
        readAsyncTransaction.read("""MATCH (node:${entityType.name} {id: '$id'} ) RETURN count(node);""")
        { resultCursor ->
            Either.conditionally(
                test = resultCursor.singleAsync().await()["count(node)"].asInt(0) == 1,
                ifTrue = {},
                ifFalse = { NotFoundError(type = entityType.name, id = id) })
        }

    private fun doNotExist(id: String, readTransaction: ReadTransaction): Either<StoreError, Unit> =
        readTransaction.read("""MATCH (node:${entityType.name} {id: '$id'} ) RETURN count(node);""") { result ->
            Either.conditionally(
                test = result.single()["count(node)"].asInt(1) == 0,
                ifTrue = {},
                ifFalse = { AlreadyExistsError(type = entityType.name, id = id) })
        }

    private suspend fun doNotExist(id: String, readAsyncTransaction: ReadAsyncTransaction): Either<StoreError, Unit> =
        readAsyncTransaction.read("""MATCH (node:${entityType.name} {id: '$id'} ) RETURN count(node);""")
        { resultCursor ->
            Either.conditionally(
                test = resultCursor.singleAsync().await()["count(node)"].asInt(1) == 0,
                ifTrue = {},
                ifFalse = { AlreadyExistsError(type = entityType.name, id = id) })
        }
}

sealed class BaseRelationStore {
    abstract fun delete(fromId: String, toId: String, writeTransaction: WriteTransaction): Either<StoreError, Unit>
}

// TODO vihang: check if relation already exists, with allow duplicate boolean flag param
class RelationStore<FROM : HasId, RELATION : Any, TO : HasId>(private val relationType: RelationType<FROM, RELATION, TO>) :
    BaseRelationStore() {

    fun create(from: FROM, relation: RELATION, to: TO, writeTransaction: WriteTransaction): Either<StoreError, Unit> {

        val properties = getStringProperties(relation as Any)
        val parameters: Map<String, Any> = mapOf("props" to properties)
        return writeTransaction.write(
            query = """
                    MATCH (from:${relationType.from.name} { id: '${from.id}' }),(to:${relationType.to.name} { id: '${to.id}' })
                    CREATE (from)-[:${relationType.name} ${'$'}props ]->(to);
                    """.trimIndent(),
            parameters = parameters
        ) { result ->

            // TODO vihang: validate if 'from' and 'to' node exists
            Either.conditionally(
                test = result.consume().counters().relationshipsCreated() == 1,
                ifTrue = {},
                ifFalse = { NotCreatedError(type = relationType.name, id = "${from.id} -> ${to.id}") })
        }
    }

    fun create(from: FROM, to: TO, writeTransaction: WriteTransaction): Either<StoreError, Unit> =
        writeTransaction.write(
            """
                MATCH (from:${relationType.from.name} { id: '${from.id}' }),(to:${relationType.to.name} { id: '${to.id}' })
                CREATE (from)-[:${relationType.name}]->(to);
                """.trimIndent()
        ) { result ->

            // TODO vihang: validate if 'from' and 'to' node exists
            Either.conditionally(
                test = result.consume().counters().relationshipsCreated() == 1,
                ifTrue = {},
                ifFalse = { NotCreatedError(type = relationType.name, id = "${from.id} -> ${to.id}") })
        }

    fun create(fromId: String, toId: String, writeTransaction: WriteTransaction): Either<StoreError, Unit> =
        writeTransaction.write(
            """
                MATCH (from:${relationType.from.name} { id: '$fromId' }),(to:${relationType.to.name} { id: '$toId' })
                CREATE (from)-[:${relationType.name}]->(to);
                """.trimIndent()
        ) { result ->

            // TODO vihang: validate if 'from' and 'to' node exists
            Either.conditionally(
                test = result.consume().counters().relationshipsCreated() == 1,
                ifTrue = {},
                ifFalse = { NotCreatedError(type = relationType.name, id = "$fromId -> $toId") })
        }

    fun create(
        fromId: String,
        relation: RELATION,
        toId: String,
        writeTransaction: WriteTransaction
    ): Either<StoreError, Unit> {

        val properties = getStringProperties(relation as Any)
        val parameters: Map<String, Any> = mapOf("props" to properties)
        return writeTransaction.write(
            query = """
                MATCH (from:${relationType.from.name} { id: '$fromId' }),(to:${relationType.to.name} { id: '$toId' })
                CREATE (from)-[:${relationType.name} ${'$'}props ]->(to);
                """.trimIndent(),
            parameters = parameters
        ) { result ->

            // TODO vihang: validate if 'from' and 'to' node exists
            Either.conditionally(
                test = result.consume().counters().relationshipsCreated() == 1,
                ifTrue = {},
                ifFalse = { NotCreatedError(type = relationType.name, id = "$fromId -> $toId") })
        }
    }

    fun create(
        fromId: String,
        toIds: Collection<String>,
        writeTransaction: WriteTransaction
    ): Either<StoreError, Unit> {

        val parameters: Map<String, Any> = mapOf("toIds" to toIds)

        return writeTransaction.write(
            """
                MATCH (to:${relationType.to.name})
                WHERE to.id in ${'$'}toIds
                WITH to
                MATCH (from:${relationType.from.name} { id: '$fromId' })
                CREATE (from)-[:${relationType.name}]->(to);
                """.trimIndent(), parameters
        ) { result ->
            // TODO vihang: validate if 'from' and 'to' node exists
            val actualCount = result.consume().counters().relationshipsCreated()
            Either.conditionally(
                test = actualCount == toIds.size,
                ifTrue = {},
                ifFalse = {
                    NotCreatedError(
                        type = relationType.name,
                        expectedCount = toIds.size,
                        actualCount = actualCount
                    )
                })
        }
    }

    fun create(
        fromIds: Collection<String>,
        toId: String,
        writeTransaction: WriteTransaction
    ): Either<StoreError, Unit> {

        val parameters: Map<String, Any> = mapOf("fromIds" to fromIds)

        return writeTransaction.write(
            """
                MATCH (from:${relationType.from.name})
                WHERE from.id in ${'$'}fromIds
                WITH from
                MATCH (to:${relationType.to.name} { id: '$toId' })
                CREATE (from)-[:${relationType.name}]->(to);
                """.trimIndent(), parameters
        ) { result ->

            // TODO vihang: validate if 'from' and 'to' node exists
            val actualCount = result.consume().counters().relationshipsCreated()
            Either.conditionally(
                test = actualCount == fromIds.size,
                ifTrue = {},
                ifFalse = {
                    NotCreatedError(
                        type = relationType.name,
                        expectedCount = fromIds.size,
                        actualCount = actualCount
                    )
                })
        }
    }

    fun get(
        fromId: String,
        toId: String,
        readTransaction: ReadTransaction
    ): Either<StoreError, List<RELATION>> {

        return relationType.from.entityStore.exists(fromId, readTransaction)
            .flatMap { relationType.to.entityStore.exists(fromId, readTransaction) }
            .flatMap {
                readTransaction.read(
                    """
                MATCH (:${relationType.from.name} { id: '$fromId' })-[r:${relationType.name}]-(:${relationType.to.name} { id: '$toId' })
                return r;
                """.trimIndent()
                ) { result ->
                    result.list { record -> relationType.createRelation(record["r"].asMap()) }
                        .right()
                }
            }
    }

    fun removeAll(toId: String, writeTransaction: WriteTransaction): Either<StoreError, Unit> = writeTransaction.write(
        """
                MATCH (from:${relationType.from.name})-[r:${relationType.name}]->(to:${relationType.to.name} { id: '$toId' })
                DELETE r;
                """.trimIndent()
    ) {
        // TODO vihang: validate if 'to' node exists
        Unit.right()
    }

    override fun delete(fromId: String, toId: String, writeTransaction: WriteTransaction): Either<StoreError, Unit> =
        writeTransaction.write(
            """
                MATCH (:${relationType.from.name} { id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
                DELETE r
                """.trimMargin()
        ) { result ->

            Either.conditionally(result.consume().counters().relationshipsDeleted() > 0,
                ifTrue = { Unit },
                ifFalse = { NotDeletedError(relationType.name, "$fromId -> $toId") })
        }
}

class UniqueRelationStore<FROM : HasId, RELATION : Any, TO : HasId>(private val relationType: RelationType<FROM, RELATION, TO>) :
    BaseRelationStore() {

    // If relation does not exists, then it creates new relation.
    fun createIfAbsent(fromId: String, toId: String, writeTransaction: WriteTransaction): Either<StoreError, Unit> {

        return relationType.from.entityStore.exists(fromId, writeTransaction)
            .flatMap {
                relationType.to.entityStore.exists(toId, writeTransaction)
            }.flatMap {

                doNotExist(fromId, toId, writeTransaction).fold(
                    { Unit.right() },
                    {
                        writeTransaction.write(
                            """
                            MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                            MERGE (fromId)-[:${relationType.name}]->(toId)
                            """.trimMargin()
                        ) { result ->

                            Either.conditionally(result.consume().counters().relationshipsCreated() == 1,
                                ifTrue = { Unit },
                                ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
                        }
                    })
            }
    }

    // If relation does not exists, then it creates new relation.
    fun createIfAbsent(
        fromId: String,
        relation: RELATION,
        toId: String,
        writeTransaction: WriteTransaction
    ): Either<StoreError, Unit> {

        return relationType.from.entityStore.exists(fromId, writeTransaction)
            .flatMap {
                relationType.to.entityStore.exists(toId, writeTransaction)
            }.flatMap {

                doNotExist(fromId, toId, writeTransaction).fold(
                    { Unit.right() },
                    {
                        // property maps cannot be used with MERGE
                        val properties = getProperties(relation as Any)
                        val strProps: String = properties.entries.joinToString(",") { """`${it.key}`: "${it.value}"""" }

                        writeTransaction.write(
                            """
                            MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                            MERGE (fromId)-[:${relationType.name} { $strProps } ]->(toId)
                            """.trimMargin()
                        ) { result ->

                            Either.conditionally(result.consume().counters().relationshipsCreated() == 1,
                                ifTrue = { Unit },
                                ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
                        }
                    })
            }
    }

    // If relation does not exists, then it creates new relation. Or else updates it.
    fun createOrUpdate(
        fromId: String,
        relation: RELATION,
        toId: String,
        writeTransaction: WriteTransaction
    ): Either<StoreError, Unit> {

        return relationType.from.entityStore.exists(fromId, writeTransaction)
            .flatMap {
                relationType.to.entityStore.exists(toId, writeTransaction)
            }.flatMap {

                doNotExist(fromId, toId, writeTransaction).fold(
                    {
                        val properties = getStringProperties(relation).toMutableMap()
                        val parameters: Map<String, Any> = mapOf("props" to properties)

                        writeTransaction.write(
                            """
                            MATCH (fromId:${relationType.from.name} {id: '$fromId'})-[r:${relationType.name}]->(toId:${relationType.to.name} {id: '$toId'})
                            SET r = ${'$'}props ;
                            """.trimMargin(),
                            parameters
                        ) { result ->
                            Either.conditionally(
                                test = result.consume().counters()
                                    .containsUpdates(), // TODO vihang: this is not perfect way to check if updates are applied
                                ifTrue = {},
                                ifFalse = { NotUpdatedError(type = relationType.name, id = "$fromId -> $toId") })
                        }
                    },
                    {
                        // property maps cannot be used with MERGE
                        val properties = getProperties(relation as Any)
                        val strProps: String = properties.entries.joinToString(",") { """`${it.key}`: "${it.value}"""" }

                        writeTransaction.write(
                            """
                            MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                            MERGE (fromId)-[:${relationType.name} { $strProps } ]->(toId)
                            """.trimMargin()
                        ) { result ->

                            Either.conditionally(result.consume().counters().relationshipsCreated() == 1,
                                ifTrue = { Unit },
                                ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
                        }
                    }
                )
            }
    }

    // If relation exists, then it fails with Already Exists Error, else it creates new relation.
    fun create(fromId: String, toId: String, writeTransaction: WriteTransaction): Either<StoreError, Unit> {

        return doNotExist(fromId, toId, writeTransaction).flatMap {
            writeTransaction.write(
                """
                MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                MERGE (fromId)-[:${relationType.name}]->(toId)
                """.trimMargin()
            ) { result ->

                Either.conditionally(result.consume().counters().relationshipsCreated() == 1,
                    ifTrue = { Unit },
                    ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
            }
        }
    }

    // If relation exists, then it fails with Already Exists Error, else it creates new relation.
    suspend fun create(
        fromId: String,
        toId: String,
        writeAsyncTransaction: WriteAsyncTransaction
    ): Either<StoreError, Unit> {

        return either {
            doNotExist(fromId, toId, writeAsyncTransaction).bind()
            writeAsyncTransaction.write(
                """
                MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                MERGE (fromId)-[:${relationType.name}]->(toId)
                """.trimMargin()
            ) { resultCursor ->

                Either.conditionally(resultCursor.consumeAsync().await().counters().relationshipsCreated() == 1,
                    ifTrue = { Unit },
                    ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
            }.bind()
        }
    }

    // If relation exists, then it fails with Already Exists Error, else it creates new relation.
    fun create(
        fromId: String,
        relation: RELATION,
        toId: String,
        writeTransaction: WriteTransaction
    ): Either<StoreError, Unit> {

        return doNotExist(fromId, toId, writeTransaction).flatMap {

            // property maps cannot be used with MERGE
            val properties = getProperties(relation as Any)
            val strProps: String = properties.entries.joinToString(",") { """`${it.key}`: "${it.value}"""" }

            writeTransaction.write(
                """
                MATCH (fromId:${relationType.from.name} {id: '$fromId'}),(toId:${relationType.to.name} {id: '$toId'})
                MERGE (fromId)-[:${relationType.name}  { $strProps } ]->(toId)
                """.trimMargin()
            ) { result ->

                Either.conditionally(result.consume().counters().relationshipsCreated() == 1,
                    ifTrue = { Unit },
                    ifFalse = { NotCreatedError(relationType.name, "$fromId -> $toId") })
            }
        }
    }

    fun get(fromId: String, toId: String, readTransaction: ReadTransaction): Either<StoreError, RELATION> =
        readTransaction.read(
            """
            MATCH (:${relationType.from.name} {id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
            RETURN r
            """.trimMargin()
        ) { result ->

            Either.conditionally(result.hasNext(),
                ifTrue = { relationType.createRelation(result.single()["r"].asMap()) },
                ifFalse = { NotFoundError(relationType.name, "$fromId -> $toId") })
                .flatMap { relation ->
                    relation.right()
                }
        }

    override fun delete(fromId: String, toId: String, writeTransaction: WriteTransaction): Either<StoreError, Unit> =
        writeTransaction.write(
            """
            MATCH (:${relationType.from.name} { id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
            DELETE r
            """.trimMargin()
        ) { result ->

            Either.conditionally(result.consume().counters().relationshipsDeleted() == 1,
                ifTrue = {},
                ifFalse = { NotDeletedError(relationType.name, "$fromId -> $toId") })
        }

    private fun doNotExist(fromId: String, toId: String, readTransaction: ReadTransaction): Either<StoreError, Unit> =
        readTransaction.read(
            """
            MATCH (:${relationType.from.name} {id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
            RETURN count(r)
            """.trimMargin()
        ) { result ->

            Either.conditionally(
                test = result.single()["count(r)"].asInt(1) == 0,
                ifTrue = {},
                ifFalse = { AlreadyExistsError(type = relationType.name, id = "$fromId -> $toId") })

        }

    private suspend fun doNotExist(
        fromId: String,
        toId: String,
        readAsyncTransaction: ReadAsyncTransaction
    ): Either<StoreError, Unit> = readAsyncTransaction.read(
        """
        MATCH (:${relationType.from.name} {id: '$fromId'})-[r:${relationType.name}]->(:${relationType.to.name} {id: '$toId'})
        RETURN count(r)
        """.trimMargin()
    ) { result ->

        Either.conditionally(
            test = result.singleAsync().await()["count(r)"].asInt(1) == 0,
            ifTrue = {},
            ifFalse = { AlreadyExistsError(type = relationType.name, id = "$fromId -> $toId") })

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
        objectMapper.convertValue(any, object : TypeReference<Map<String, Any?>>() {})
    )

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
