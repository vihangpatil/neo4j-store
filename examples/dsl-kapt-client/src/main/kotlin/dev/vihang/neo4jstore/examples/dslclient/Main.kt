package dev.vihang.neo4jstore.examples.dslclient

import arrow.core.Either
import arrow.core.extensions.fx
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.writeTransaction
import dev.vihang.neo4jstore.dsl.create
import dev.vihang.neo4jstore.dsl.update
import dev.vihang.neo4jstore.dsl.get
import dev.vihang.neo4jstore.dsl.link
import dev.vihang.neo4jstore.error.StoreError

fun main() {
    // init
    ConfigRegistry.config = Config()
    Neo4jClient.start()

    try {
        writeTransaction {
            Either.fx<StoreError, Unit> {

                // create users
                create { User(id = "id1", name = "Alice") }.bind()
                create { User(id = "id2", name = "Bob") }.bind()

                println(get(User withId "id1").bind())
                println(get(User withId "id2").bind())


                // create roles
                create { Role(id = "admin", description = "Admin User") }
                create { Role(id = "user", description = "Simple User") }

                println(get(Role withId "admin").bind())
                println(get(Role withId "user").bind())


                // link users with roles
                link { (User withId "id1") hasRole (Role withId "admin") }
                link { (Role withId "user") ofUser (User withId "id2") }

                println(get(User withRole (Role withId "admin")).bind())
                println(get(Role ofUser (User withId "id2")).bind())

                // update users
                update { User(id = "id1", name = "Foo") }
                update { User(id = "id2", name = "Bar") }

                println(get(User withId "id1").bind())
                println(get(User withId "id2").bind())
            }
        }
    } finally {
        Neo4jClient.stop()
    }
}