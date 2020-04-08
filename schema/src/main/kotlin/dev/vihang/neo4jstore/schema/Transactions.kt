package dev.vihang.neo4jstore.schema

import dev.vihang.neo4jstore.client.Neo4jClient
import org.neo4j.driver.AccessMode.READ
import org.neo4j.driver.AccessMode.WRITE
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.Transaction

fun <R> readTransaction(action: ReadTransaction.() -> R): R {

    val sessionConfig = SessionConfig.builder()
            .withDefaultAccessMode(READ)
            .build()

    val session = Neo4jClient.driver.session(sessionConfig)

    return session.readTransaction { transaction ->
        val result = ReadTransaction(transaction).action()
        transaction.close()
        result
    }
}

fun <R> writeTransaction(action: WriteTransaction.() -> R): R {
    val sessionConfig = SessionConfig.builder()
            .withDefaultAccessMode(WRITE)
            .build()

    val session = Neo4jClient.driver.session(sessionConfig)

    return session.writeTransaction { transaction ->
        val result = WriteTransaction(transaction).action()
        transaction.close()
        result
    }
}

open class ReadTransaction(open val transaction: Transaction)
open class WriteTransaction(open val transaction: Transaction)