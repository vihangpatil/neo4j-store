package dev.vihang.neo4jstore.examples.storeclient

import arrow.core.Either
import arrow.core.extensions.fx
import arrow.core.flatMap
import arrow.core.right
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.writeAsyncTransaction
import dev.vihang.neo4jstore.schema.RelationType
import dev.vihang.neo4jstore.schema.UniqueRelationStore
import dev.vihang.neo4jstore.schema.entityStore
import dev.vihang.neo4jstore.schema.flatMapSuspend
import kotlinx.coroutines.runBlocking


fun main() {

    // init
    ConfigRegistry.config = Config()
    Neo4jClient.start()

    try {
        // using [Entity], create [EntityStore]
        val userStore = User::class.entityStore
        val roleStore = Role::class.entityStore

        val hasRoleType = RelationType(relation = hasRoleRelation)

        val hasRoleStore = UniqueRelationStore(relationType = hasRoleType)

        runBlocking {
            writeAsyncTransaction {
                userStore.create(User(id = "id1", name = "Alice"), writeAsyncTransaction = this@writeAsyncTransaction).flatMapSuspend {
                    userStore.create(User(id = "id2", name = "Bob"), writeAsyncTransaction = this@writeAsyncTransaction)
                }.flatMapSuspend {
                    roleStore.create(Role(id = "admin", description = "Admin User"), writeAsyncTransaction = this@writeAsyncTransaction)
                }.flatMapSuspend {
                    roleStore.create(Role(id = "user", description = "Simple User"), writeAsyncTransaction = this@writeAsyncTransaction)
                }.flatMapSuspend {
                    // NOTE: [WriteTransaction] can be used as [ReadTransaction]
                    userStore.get("id1", readAsyncTransaction = this@writeAsyncTransaction)
                            .flatMap {
                                println(it).right()
                            }
                }.flatMapSuspend {
                    userStore.get("id2", readAsyncTransaction = this@writeAsyncTransaction)
                            .flatMap {
                                println(it).right()
                            }
                }.flatMapSuspend {
                    roleStore.get("admin", readAsyncTransaction = this@writeAsyncTransaction)
                            .flatMap {
                                println(it).right()
                            }
                }.flatMapSuspend {
                    roleStore.get("user", readAsyncTransaction = this@writeAsyncTransaction)
                            .flatMap {
                                println(it).right()
                            }
                }.flatMapSuspend {
                    hasRoleStore.create(fromId = "id1", toId = "admin", writeAsyncTransaction = this@writeAsyncTransaction)
                }.flatMapSuspend {
                    hasRoleStore.create(fromId = "id2", toId = "user", writeAsyncTransaction = this@writeAsyncTransaction)
                }.flatMapSuspend {
                    userStore.getRelated("id1", hasRoleType, readAsyncTransaction = this@writeAsyncTransaction).flatMap {
                        println(it.single()).right()
                    }
                }.flatMapSuspend {
                    roleStore.getRelatedFrom("user", hasRoleType, readAsyncTransaction = this@writeAsyncTransaction).flatMap {
                        println(it.single()).right()
                    }
                }
            }
        }
    } finally {
        Neo4jClient.stop()
    }
}