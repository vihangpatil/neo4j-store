package dev.vihang.neo4jstore.examples.cypherclient

import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.read
import dev.vihang.neo4jstore.client.readTransaction
import dev.vihang.neo4jstore.client.write
import dev.vihang.neo4jstore.client.writeTransaction
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {

    // init
    ConfigRegistry.config = Config()
    Neo4jClient.start()

    try {
        readTransaction {
            read(query = "MATCH (n) RETURN n;") {
                result ->
                println("Nodes deleted: ${result.consume().counters().nodesDeleted()}")
            }
        }

        writeTransaction {
            write(query = "MATCH (n) DELETE n;") { result ->
                println("Nodes deleted: ${result.consume().counters().nodesDeleted()}")
            }
        }

    } finally {
        Neo4jClient.stop()
    }
}