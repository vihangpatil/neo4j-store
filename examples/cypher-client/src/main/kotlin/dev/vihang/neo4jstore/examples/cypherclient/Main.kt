package dev.vihang.neo4jstore.examples.cypherclient

import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.schema.Graph.read
import dev.vihang.neo4jstore.schema.Graph.write
import dev.vihang.neo4jstore.schema.readTransaction
import dev.vihang.neo4jstore.schema.writeTransaction

fun main() {

    // init
    ConfigRegistry.config = Config()
    Neo4jClient.start()

    try {
        readTransaction {
            read(query = "MATCH (n) RETURN n;", transaction = transaction) {
                result ->
                println("Nodes deleted: ${result.consume().counters().nodesDeleted()}")
            }
        }

        writeTransaction {
            write(query = "MATCH (n) DELETE n;", transaction = transaction) {
                result ->
                println("Nodes deleted: ${result.consume().counters().nodesDeleted()}")
            }
        }
    } finally {
        Neo4jClient.stop()
    }
}