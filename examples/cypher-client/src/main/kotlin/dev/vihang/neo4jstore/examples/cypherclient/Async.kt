package dev.vihang.neo4jstore.examples.cypherclient

import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.read
import dev.vihang.neo4jstore.client.readAsyncTransaction
import dev.vihang.neo4jstore.client.write
import dev.vihang.neo4jstore.client.writeAsyncTransaction
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {

    // init
    ConfigRegistry.config = Config()
    Neo4jClient.start()

    try {
        readAsyncTransaction {
            read(query = "MATCH (n) RETURN n;") {
                resultCursor ->
                val nodesDeleted = resultCursor.consumeAsync().await().counters().nodesDeleted()
                println("Nodes deleted: $nodesDeleted")
                nodesDeleted
            }
        }

        writeAsyncTransaction {
            write(query = "MATCH (n) DELETE n;") {
                resultCursor ->
                println("Nodes deleted: ${resultCursor.consumeAsync().await().counters().nodesDeleted()}")
            }
        }
    } finally {
        Neo4jClient.stop()
    }
}