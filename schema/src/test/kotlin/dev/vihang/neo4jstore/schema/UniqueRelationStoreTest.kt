package dev.vihang.neo4jstore.schema

import arrow.core.Either
import arrow.core.extensions.fx
import com.palantir.docker.compose.DockerComposeExtension
import com.palantir.docker.compose.connection.waiting.HealthChecks
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.write
import dev.vihang.neo4jstore.client.writeTransaction
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.schema.model.HasId
import dev.vihang.neo4jstore.schema.model.Relation
import org.amshove.kluent.`should be equal to`
import org.joda.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.fail

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UniqueRelationStoreTest {

    // Entity classes

    data class Identity(override val id: String, val type: String) : HasId {
        companion object
    }

    data class User(override val id: String, val name: String) : HasId {
        companion object
    }

    // Relation class

    data class Identifies(val provider: String)

    // schema
    private val identifiesRelation = Relation(
            name = "IDENTIFIES",
            from = Identity::class,
            relation = Identifies::class,
            to = User::class
    )

    private val identityStore = Identity::class.entityStore
    private val userStore = User::class.entityStore
    private val identifiesType = RelationType(identifiesRelation)
    private val identifiesStore = UniqueRelationStore(identifiesType)

    @Test
    fun createIfAbsent() {

        writeTransaction {
            Either.fx<StoreError, Unit> {

                // create entities

                identityStore.create(Identity(id = "foo@bar.com", type = "EMAIL"), this@writeTransaction).bind()
                userStore.create(User(id = "some_user", name = "Test User"), this@writeTransaction).bind()

                // create relation
                identifiesStore.createIfAbsent(
                        fromId = "foo@bar.com",
                        toId = "some_user",
                        relation = Identifies(provider = "bar.com"),
                        writeTransaction = this@writeTransaction
                ).bind()

                // attempt to create duplicate relation
                identifiesStore.createIfAbsent(
                        fromId = "foo@bar.com",
                        toId = "some_user",
                        relation = Identifies(provider = "test.com"), // changed property
                        writeTransaction = this@writeTransaction
                ).bind()

                write("MATCH (:Identity)-[r:IDENTIFIES]->(:User) RETURN r;") { result ->
                    val recordList = result.list()
                    // should have only one unique relation
                    recordList.size `should be equal to` 1
                    // that relation should not be updated and should be one which was created first
                    identifiesType.createRelation(recordList.single()["r"].asMap()) `should be equal to` Identifies(provider = "bar.com")
                }

                Unit
            }
        }.mapLeft {
            fail {
                it.message
            }
        }
    }

    @Test
    fun createOrUpdate() {

        writeTransaction {
            Either.fx<StoreError, Unit> {

                // create entities

                identityStore.create(Identity(id = "foo@bar.com", type = "EMAIL"), this@writeTransaction).bind()
                userStore.create(User(id = "some_user", name = "Test User"), this@writeTransaction).bind()

                // create relation
                identifiesStore.createOrUpdate(
                        fromId = "foo@bar.com",
                        toId = "some_user",
                        relation = Identifies(provider = "bar.com"),
                        writeTransaction = this@writeTransaction
                ).bind()

                // attempt to create duplicate relation
                identifiesStore.createOrUpdate(
                        fromId = "foo@bar.com",
                        toId = "some_user",
                        relation = Identifies(provider = "test.com"),
                        writeTransaction = this@writeTransaction
                ).bind()

                write("MATCH (:Identity)-[r:IDENTIFIES]->(:User) RETURN r;") { result ->
                    val recordList = result.list()
                    // should have only one unique relation
                    recordList.size `should be equal to` 1
                    // that relation should be updated one
                    identifiesType.createRelation(recordList.single()["r"].asMap()) `should be equal to` Identifies(provider = "test.com")
                }

                Unit
            }
        }.mapLeft {
            fail {
                it.message
            }
        }
    }

    @BeforeEach
    fun clear() {
        writeTransaction {
            write("MATCH (n) DETACH DELETE n;") {

            }
        }
    }

    @BeforeAll
    fun beforeAll() {
        ConfigRegistry.config = Config()
        Neo4jClient.start()
    }

    @AfterAll
    fun afterAll() {
        Neo4jClient.stop()
    }

    companion object {

        @RegisterExtension
        @JvmField
        var docker: DockerComposeExtension = DockerComposeExtension.builder()
                .file("src/test/resources/docker-compose.yaml")
                .waitingForService("neo4j", HealthChecks.toHaveAllPortsOpen())
                .waitingForService("neo4j",
                        HealthChecks.toRespond2xxOverHttp(7474) { port ->
                            port.inFormat("http://\$HOST:\$EXTERNAL_PORT/browser")
                        },
                        Duration.standardSeconds(40L))
                .build()
    }
}