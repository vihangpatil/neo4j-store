package dev.vihang.neo4jstore.examples.storeclient

import arrow.core.Either
import arrow.core.extensions.fx
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.examples.model.Role
import dev.vihang.neo4jstore.examples.model.User
import dev.vihang.neo4jstore.schema.EntityStore
import dev.vihang.neo4jstore.schema.EntityType
import dev.vihang.neo4jstore.schema.writeTransaction

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

        writeTransaction {
            Either.fx<StoreError, Unit> {

                userStore.create(User(id = "id1", name = "Alice"), transaction = transaction).bind()
                userStore.create(User(id = "id2", name = "Bob"), transaction = transaction).bind()

                roleStore.create(Role(id = "admin", description = "Admin User"), transaction = transaction).bind()
                roleStore.create(Role(id = "user", description = "Simple User"), transaction = transaction).bind()

                val user1: User = userStore.get("id1", transaction = transaction).bind()
                println(user1)
                val user2: User = userStore.get("id2", transaction = transaction).bind()
                println(user2)

                val admin: Role = roleStore.get("admin", transaction = transaction).bind()
                println(admin)
                val user: Role = roleStore.get("user", transaction = transaction).bind()
                println(user)
            }
        }

    } finally {
        Neo4jClient.stop()
    }
}