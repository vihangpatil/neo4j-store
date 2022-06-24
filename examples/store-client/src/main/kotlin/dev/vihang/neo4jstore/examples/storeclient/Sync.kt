package dev.vihang.neo4jstore.examples.storeclient

import arrow.core.continuations.either
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.writeTransaction
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

        writeTransaction {
            either<StoreError, Unit> {

                userStore.create(User(id = "id1", name = "Alice"), writeTransaction = this@writeTransaction).bind()
                userStore.create(User(id = "id2", name = "Bob"), writeTransaction = this@writeTransaction).bind()

                roleStore.create(Role(id = "admin", description = "Admin User"), writeTransaction = this@writeTransaction).bind()
                roleStore.create(Role(id = "user", description = "Simple User"), writeTransaction = this@writeTransaction).bind()

                // NOTE: [WriteTransaction] can be used as [ReadTransaction]
                val user1: User = userStore.get("id1", readTransaction = this@writeTransaction).bind()
                println(user1)
                val user2: User = userStore.get("id2", readTransaction = this@writeTransaction).bind()
                println(user2)

                val admin: Role = roleStore.get("admin", readTransaction = this@writeTransaction).bind()
                println(admin)
                val user: Role = roleStore.get("user", readTransaction = this@writeTransaction).bind()
                println(user)

                hasRoleStore.create(fromId = "id1", toId = "admin", writeTransaction = this@writeTransaction).bind()
                hasRoleStore.create(fromId = "id2", toId = "user", writeTransaction = this@writeTransaction).bind()

                val relatedRole: Role = userStore.getRelated("id1", hasRoleType, readTransaction = this@writeTransaction)
                    .bind()
                    .single()
                println(relatedRole)

                val relatedUser: User = roleStore.getRelatedFrom("user", hasRoleType, readTransaction = this@writeTransaction)
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