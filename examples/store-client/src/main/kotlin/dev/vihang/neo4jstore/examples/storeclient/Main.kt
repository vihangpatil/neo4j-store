package dev.vihang.neo4jstore.examples.storeclient

import arrow.core.Either
import arrow.core.extensions.fx
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.writeTransaction
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.examples.model.Role
import dev.vihang.neo4jstore.examples.model.User
import dev.vihang.neo4jstore.examples.model.hasRoleRelation
import dev.vihang.neo4jstore.schema.EntityStore
import dev.vihang.neo4jstore.schema.EntityType
import dev.vihang.neo4jstore.schema.None
import dev.vihang.neo4jstore.schema.RelationType
import dev.vihang.neo4jstore.schema.UniqueRelationStore

fun main() {

    // init
    ConfigRegistry.config = Config()
    Neo4jClient.start()

    try {
        // using entity classes in `examples/model` project, create [EntityType]
        val userEntityType = EntityType(User::class.java)
        val roleEntityType = EntityType(Role::class.java)

        // using [EntityType], create [EntityStore]
        val userStore = EntityStore(userEntityType)
        val roleStore = EntityStore(roleEntityType)

        val hasRoleType = RelationType(
                relation = hasRoleRelation,
                from = userEntityType,
                to = roleEntityType,
                dataClass = None::class.java
        )

        val hasRoleStore = UniqueRelationStore(relationType = hasRoleType)

        writeTransaction {
            Either.fx<StoreError, Unit> {

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

                val relatedUser = roleStore.getRelatedFrom("user", hasRoleType, readTransaction = this@writeTransaction)
                        .bind()
                        .single()
                println(relatedUser)

            }
        }

    } finally {
        Neo4jClient.stop()
    }
}