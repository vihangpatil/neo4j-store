package dev.vihang.neo4jstore.client

import dev.vihang.common.logging.getLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.neo4j.driver.AccessMode.READ
import org.neo4j.driver.AccessMode.WRITE
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.Result
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.Transaction
import org.neo4j.driver.async.AsyncSession
import org.neo4j.driver.async.AsyncTransaction
import org.neo4j.driver.async.ResultCursor
import java.net.URI
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Config for Neo4jClient
 */
data class Config(
    val host: String = "localhost",
    val protocol: String = "bolt",
    val port: Int = 7687,
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
            with(ConfigRegistry.config) { URI("$protocol://$host:$port") },
            AuthTokens.none(),
            config
        ) ?: throw Exception("Unable to get Neo4j client driver instance")
    }

    fun stop() {
        driver.close()
    }
}

//
// Session factories
//

// Sync Session factories

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

// Async Session factories

fun createReadAsyncSession(): AsyncSession {
    val sessionConfig = SessionConfig.builder()
        .withDefaultAccessMode(READ)
        .build()

    return Neo4jClient.driver.asyncSession(sessionConfig)
}

fun createWriteAsyncSession(): AsyncSession {
    val sessionConfig = SessionConfig.builder()
        .withDefaultAccessMode(WRITE)
        .build()

    return Neo4jClient.driver.asyncSession(sessionConfig)
}

//
// Transactions
//

// Sync Transactions

open class ReadTransaction(
    open val transaction: Transaction,
) {
    open val logger by getLogger()
}

open class WriteTransaction(
    override val transaction: Transaction,
) : ReadTransaction(transaction = transaction) {
    override val logger by getLogger()
}

// Async Transactions

open class ReadAsyncTransaction(
    open val transaction: AsyncTransaction,
) {
    open val logger by getLogger()
}

open class WriteAsyncTransaction(
    override val transaction: AsyncTransaction,
) : ReadAsyncTransaction(transaction = transaction) {
    override val logger by getLogger()
}

//
// Transactions Scopes
//

// Sync Transactions Scopes

/**
 * [ReadTransaction] scope
 */
suspend fun <R> readTransaction(
    action: ReadTransaction.() -> R,
): R {
    return createReadSession().use { session ->
        val transaction = session.beginTransaction()
        withContext(Dispatchers.IO) {
            ReadTransaction(transaction).action()
        }
    }
}

/**
 * [WriteTransaction] scope
 */
suspend fun <R> writeTransaction(
    action: suspend WriteTransaction.() -> R,
): R {
    return createWriteSession().use { session ->
        val transaction = session.beginTransaction()
        withContext(Dispatchers.IO) {
            WriteTransaction(transaction).action()
        }
    }
}

// Async Transactions Scopes

/**
 * [ReadAsyncTransaction] scope
 */
suspend fun <R> readAsyncTransaction(
    action: suspend ReadAsyncTransaction.() -> R,
): R {
    val session = createReadAsyncSession()
    try {
        val transaction = session.beginTransactionAsync().await()
        return withContext(Dispatchers.IO) {
            ReadAsyncTransaction(transaction).action()
        }
    } finally {
        session.closeAsync().await()
    }
}

/**
 * [WriteAsyncTransaction] scope
 */
suspend fun <R> writeAsyncTransaction(
    action: suspend WriteAsyncTransaction.() -> R,
): R {
    val session = createWriteAsyncSession()
    try {
        val transaction = session.beginTransactionAsync().await()
        return withContext(Dispatchers.IO) {
            val result = WriteAsyncTransaction(transaction).action()
            transaction.commitAsync().await()
            result
        }
    } finally {
        session.closeAsync().await()
    }
}

//
// Transaction Scope Extensions
//

// Sync Transaction Scope Extensions

/**
 * Cypher Query Runner using [WriteTransaction]
 */
fun <R> WriteTransaction.write(
    query: String,
    parameters: Map<String, Any> = emptyMap(),
    transform: (Result) -> R,
): R {
    logger.trace("write:[\n$query\n]")
    val result = transaction.run(query, parameters)
    return transform(result)
}

/**
 * Cypher Query Runner using [ReadTransaction]
 */
fun <R> ReadTransaction.read(
    query: String,
    parameters: Map<String, Any> = emptyMap(),
    transform: (Result) -> R,
): R {
    logger.trace("read:[\n$query\n]")
    val result = transaction.run(query, parameters)
    return transform(result)
}

// Async Transaction Scope Extensions

/**
 * Cypher Query Runner using [WriteAsyncTransaction]
 */
suspend fun <R> WriteAsyncTransaction.write(
    query: String,
    parameters: Map<String, Any> = emptyMap(),
    transform: suspend (ResultCursor) -> R,
): R {
    logger.trace("write:[\n$query\n]")
    val result = transaction.runAsync(query, parameters).await()
    return transform(result)
}

/**
 * Cypher Query Runner using [ReadAsyncTransaction]
 */
suspend fun <R> ReadAsyncTransaction.read(
    query: String,
    parameters: Map<String, Any> = emptyMap(),
    transform: suspend (ResultCursor) -> R,
): R {
    logger.trace("read:[\n$query\n]")
    val result = transaction.runAsync(query, parameters).await()
    return transform(result)
}
