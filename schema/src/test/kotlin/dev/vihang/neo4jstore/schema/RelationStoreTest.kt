package dev.vihang.neo4jstore.schema

import arrow.core.continuations.either
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.read
import dev.vihang.neo4jstore.client.write
import dev.vihang.neo4jstore.client.writeTransaction
import dev.vihang.neo4jstore.error.StoreError
import dev.vihang.neo4jstore.schema.model.HasId
import dev.vihang.neo4jstore.schema.model.None
import dev.vihang.neo4jstore.schema.model.Relation
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import org.testcontainers.containers.Neo4jContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RelationStoreTest {

    // Entity classes

    data class Identity(override val id: String, val type: String) : HasId {
        companion object
    }

    data class User(override val id: String, val name: String) : HasId {
        companion object
    }

    // schema
    private val identifiesRelation = Relation(
        name = "IDENTIFIES",
        from = Identity::class,
        relation = None::class,
        to = User::class
    )

    private val identityStore = Identity::class.entityStore
    private val userStore = User::class.entityStore
    private val identifiesType = RelationType(identifiesRelation)
    private val identifiesStore = RelationStore(identifiesType)

    @Test
    fun createFromIds() = runBlocking {
        writeTransaction {
            either<StoreError, Unit> {

                // create entities

                identityStore.create(Identity(id = "one@foo.com", type = "EMAIL"), this@writeTransaction).bind()
                identityStore.create(Identity(id = "two@foo.com", type = "EMAIL"), this@writeTransaction).bind()
                identityStore.create(Identity(id = "three@foo.com", type = "EMAIL"), this@writeTransaction).bind()

                userStore.create(User(id = "some_user", name = "Test User"), this@writeTransaction).bind()

                // create relation
                identifiesStore.create(
                    fromIds = listOf("one@foo.com", "two@foo.com", "three@foo.com"),
                    toId = "some_user",
                    writeTransaction = this@writeTransaction
                ).bind()

                read("MATCH (:Identity)-[r:IDENTIFIES]->(:User) RETURN r;") { result ->
                    val recordList = result.list()
                    // should have 3 relations
                    recordList.size `should be equal to` 3
                }

                Unit
            }
        }.mapLeft {
            fail {
                it.message
            }
        }
        Unit
    }

    @Test
    fun createToIds() = runBlocking {
        writeTransaction {
            either<StoreError, Unit> {

                // create entities

                identityStore.create(Identity(id = "foo@bar.com", type = "EMAIL"), this@writeTransaction).bind()

                userStore.create(User(id = "user_1", name = "Test User 1"), this@writeTransaction).bind()
                userStore.create(User(id = "user_2", name = "Test User 2"), this@writeTransaction).bind()
                userStore.create(User(id = "user_3", name = "Test User 3"), this@writeTransaction).bind()

                // create relation
                identifiesStore.create(
                    fromId = "foo@bar.com",
                    toIds = listOf("user_1", "user_2", "user_3"),
                    writeTransaction = this@writeTransaction
                ).bind()

                read("MATCH (:Identity)-[r:IDENTIFIES]->(:User) RETURN r;") { result ->
                    val recordList = result.list()
                    // should have 3 relations
                    recordList.size `should be equal to` 3
                }

                Unit
            }
        }.mapLeft {
            fail {
                it.message
            }
        }
        Unit
    }

    @BeforeEach
    fun clear() = runBlocking {
        writeTransaction {
            write("MATCH (n) DETACH DELETE n;") {}
        }
    }

    @BeforeAll
    fun beforeAll() {
        ConfigRegistry.config = Config(port = neo4jContainer.firstMappedPort)
        Neo4jClient.start()
    }

    @AfterAll
    fun afterAll() {
        neo4jContainer.stop()
    }

    companion object {

        @Container
        @JvmStatic
        val neo4jContainer: Neo4jContainer<*> = Neo4jContainer(DockerImageName.parse("neo4j:4.4.8"))
            .withoutAuthentication()
    }
}