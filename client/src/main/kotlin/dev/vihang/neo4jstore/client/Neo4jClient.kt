package dev.vihang.neo4jstore.client

import dev.vihang.common.logging.getLogger
import org.neo4j.driver.AccessMode.READ
import org.neo4j.driver.AccessMode.WRITE
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Result
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.Transaction
import java.net.URI
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Config for Neo4jClient
 */
data class Config(
        val host: String = "localhost",
        val protocol: String = "bolt"
)

object ConfigRegistry {
    lateinit var config: Config
}

object Neo4jClient {

    // use "bolt+routing://neo4j:7687" for clustered Neo4j
    // Explore config and auth
    lateinit var driver: Driver

    fun start() {
        val config = org.neo4j.driver.Config.builder()
                .withoutEncryption()
                .withConnectionTimeout(10, SECONDS)
                .withMaxConnectionPoolSize(1000)
                .build()
        driver = GraphDatabase.driver(
                URI("${ConfigRegistry.config.protocol}://${ConfigRegistry.config.host}:7687"),
                AuthTokens.none(),
                config) ?: throw Exception("Unable to get Neo4j client driver instance")
    }

    fun stop() {
        driver.close()
    }
}

fun createReadSession(): Session {
    val sessionConfig = SessionConfig.builder()
            .withDefaultAccessMode(READ)
            .build()

    return Neo4jClient.driver.session(sessionConfig)
}

fun createWriteSession(): Session {
    val sessionConfig = SessionConfig.builder()
            .withDefaultAccessMode(WRITE)
            .build()

    return Neo4jClient.driver.session(sessionConfig)
}

open class ReadTransaction(open val transaction: Transaction) {
    open val logger by getLogger()
}

open class WriteTransaction(override val transaction: Transaction) : ReadTransaction(transaction = transaction) {
    override val logger by getLogger()
}

/**
 * [ReadTransaction] context / factory
 */
fun <R> readTransaction(action: ReadTransaction.() -> R): R = createReadSession()
        .readTransaction { transaction ->
            ReadTransaction(transaction).action()
        }

/**
 * [WriteTransaction] context / factory
 */
fun <R> writeTransaction(action: WriteTransaction.() -> R): R = createWriteSession()
        .writeTransaction { transaction ->
            WriteTransaction(transaction).action()
        }

/**
 * Cypher Query Runner using [WriteTransaction]
 */
fun <R> WriteTransaction.write(query: String, parameters: Map<String, Any> = emptyMap(), transform: (Result) -> R): R {
    logger.trace("write:[\n$query\n]")
    return transaction
            .run(query, parameters)
            .let(transform)
}

/**
 * Cypher Query Runner using [ReadTransaction]
 */
fun <R> ReadTransaction.read(query: String, parameters: Map<String, Any> = emptyMap(), transform: (Result) -> R): R {
    logger.trace("read:[\n$query\n]")
    return transaction
            .run(query, parameters)
            .let(transform)
}
