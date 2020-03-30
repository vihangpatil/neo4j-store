package dev.vihang.neo4jstore.client

import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import java.net.URI
import java.util.concurrent.TimeUnit.SECONDS

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