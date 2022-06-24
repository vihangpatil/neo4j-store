package dev.vihang.neo4jstore.examples.storeclient

import arrow.core.continuations.either
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.writeAsyncTransaction
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.schema.RelationType
import dev.vihang.neo4jstore.schema.UniqueRelationStore
import dev.vihang.neo4jstore.schema.entityStore
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {

    // init
    ConfigRegistry.config = Config()
    Neo4jClient.start()

    try {
        // using [Entity], create [EntityStore]
        val userStore = User::class.entityStore
        val roleStore = Role::class.entityStore

        val hasRoleType = RelationType(relation = hasRoleRelation)

        val hasRoleStore = UniqueRelationStore(relationType = hasRoleType)

        writeAsyncTransaction {
            either<StoreError, Unit> {

                userStore.create(User(id = "id1", name = "Alice"), writeAsyncTransaction = this@writeAsyncTransaction).bind()
                userStore.create(User(id = "id2", name = "Bob"), writeAsyncTransaction = this@writeAsyncTransaction).bind()

                roleStore.create(Role(id = "admin", description = "Admin User"), writeAsyncTransaction = this@writeAsyncTransaction).bind()
                roleStore.create(Role(id = "user", description = "Simple User"), writeAsyncTransaction = this@writeAsyncTransaction).bind()

                // NOTE: [WriteTransaction] can be used as [ReadTransaction]
                val user1: User = userStore.get("id1", readAsyncTransaction = this@writeAsyncTransaction).bind()
                println(user1)
                val user2: User = userStore.get("id2", readAsyncTransaction = this@writeAsyncTransaction).bind()
                println(user2)

                val adminRole: Role = roleStore.get("admin", readAsyncTransaction = this@writeAsyncTransaction).bind()
                println(adminRole)
                val userRole: Role = roleStore.get("user", readAsyncTransaction = this@writeAsyncTransaction).bind()
                println(userRole)

                hasRoleStore.create(fromId = "id1", toId = "admin", writeAsyncTransaction = this@writeAsyncTransaction).bind()
                hasRoleStore.create(fromId = "id2", toId = "user", writeAsyncTransaction = this@writeAsyncTransaction).bind()

                val relatedRole: Role = userStore.getRelated("id1", hasRoleType, readAsyncTransaction = this@writeAsyncTransaction)
                    .bind()
                    .single()
                println(relatedRole)

                val relatedUser: User = roleStore.getRelatedFrom("user", hasRoleType, readAsyncTransaction = this@writeAsyncTransaction)
                    .bind()
                    .single()
                println(relatedUser)
            }
        }
    } finally {
        Neo4jClient.stop()
    }
    Unit
}