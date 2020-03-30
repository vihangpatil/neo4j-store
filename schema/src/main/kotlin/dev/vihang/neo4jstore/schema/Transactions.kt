package dev.vihang.neo4jstore.schema

import dev.vihang.neo4jstore.client.Neo4jClient
import org.neo4j.driver.v1.AccessMode.READ
import org.neo4j.driver.v1.AccessMode.WRITE
import org.neo4j.driver.v1.Transaction

fun <R> readTransaction(action: ReadTransaction.() -> R): R =
        Neo4jClient.driver.session(READ)
                .use { session ->
                    session.readTransaction { transaction ->
                        val result = action(ReadTransaction(transaction))
                        transaction.close()
                        result
                    }
                }

fun <R> writeTransaction(action: WriteTransaction.() -> R): R =
        Neo4jClient.driver.session(WRITE)
                .use { session ->
                    session.writeTransaction { transaction ->
                        val result = action(WriteTransaction(transaction))
                        transaction.close()
                        result
                    }
                }

open class ReadTransaction(open val transaction: Transaction)
open class WriteTransaction(open val transaction: Transaction)