package dev.viahng.iam

import arrow.core.Either
import arrow.core.extensions.fx
import com.palantir.docker.compose.DockerComposeExtension
import com.palantir.docker.compose.connection.waiting.HealthChecks
import dev.vihang.iam.model.Action
import dev.vihang.iam.model.Identity
import dev.vihang.iam.model.Permission
import dev.vihang.iam.model.Resource
import dev.vihang.iam.model.Role
import dev.vihang.iam.model.User
import dev.vihang.iam.model.forResource
import dev.vihang.iam.model.hasIdentity
import dev.vihang.iam.model.hasPermission
import dev.vihang.iam.model.hasRole
import dev.vihang.iam.model.identifies
import dev.vihang.iam.model.isPermittedBy
import dev.vihang.iam.model.isPermittedFor
import dev.vihang.iam.model.isRoleOf
import dev.vihang.iam.model.ofUser
import dev.vihang.iam.model.permitsAction
import dev.vihang.iam.model.permittedBy
import dev.vihang.iam.model.permittedForRole
import dev.vihang.iam.model.permittedTo
import dev.vihang.iam.model.resourceToPermission
import dev.vihang.iam.model.toResource
import dev.vihang.iam.model.withId
import dev.vihang.iam.model.withIdentity
import dev.vihang.iam.model.withPermission
import dev.vihang.iam.model.withRole
import dev.vihang.neo4jstore.client.Config
import dev.vihang.neo4jstore.client.ConfigRegistry
import dev.vihang.neo4jstore.client.Neo4jClient
import dev.vihang.neo4jstore.client.write
import dev.vihang.neo4jstore.client.writeTransaction
import dev.vihang.neo4jstore.dsl.create
import dev.vihang.neo4jstore.dsl.get
import dev.vihang.neo4jstore.dsl.link
import dev.vihang.neo4jstore.error.StoreError
import org.amshove.kluent.`should be equal to`
import org.joda.time.Duration
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.test.assertEquals

class Tests {

    @Test
    fun test() {
        writeTransaction {
            Either.fx<StoreError, Unit> {

                // create entities

                create { Identity(id = "some_id", type = "id_type", provider = "unit_test") }.bind()
                create { User(id = "some_user") }.bind()
                create { Role(id = "admin", description = "Admin User") }.bind()
                create { Permission(id = "database.write") }.bind()
                create { Resource(id = "database") }.bind()
                create { Action(id = "write") }.bind()

                // create relations

                link { (Identity withId "some_id") identifies (User withId "some_user") }.bind()
                link { (User withId "some_user") hasIdentity (Identity withId "some_id") }.bind()

                link { (User withId "some_user") hasRole (Role withId "admin") }.bind()
                link { (Role withId "admin") isRoleOf (User withId "some_user") }.bind()

                link { (Role withId "admin") hasPermission (Permission withId "database.write") }.bind()
                link { (Permission withId "database.write") isPermittedFor (Role withId "admin") }.bind()

                link { (Permission withId "database.write") forResource (Resource withId "database") }.bind()
                link { (Resource withId "database") resourceToPermission (Permission withId "database.write") }.bind()

                link { (Permission withId "database.write") permitsAction (Action withId "write") }.bind()
                link { (Action withId "write") isPermittedBy (Permission withId "database.write") }.bind()

                // read

                // get withId

                get(Identity withId "some_id").bind() `should be equal to`
                        Identity(id = "some_id", type = "id_type", provider = "unit_test")

                get(User withId "some_user").bind() `should be equal to`
                        User(id = "some_user")

                get(Role withId "admin").bind() `should be equal to`
                        Role(id = "admin", description = "Admin User")

                get(Permission withId "database.write").bind() `should be equal to`
                        Permission(id = "database.write")

                get(Resource withId "database").bind() `should be equal to`
                        Resource(id = "database")

                get(Action withId "write").bind() `should be equal to`
                        Action(id = "write")

                // get Related

                get(Identity ofUser (User withId "some_user")).bind().single() `should be equal to`
                        Identity(id = "some_id", type = "id_type", provider = "unit_test")

                get(User withIdentity (Identity withId "some_id")).bind().single() `should be equal to`
                        User(id = "some_user")

                get(User withRole (Role withId "admin")).bind().single() `should be equal to`
                        User(id = "some_user")

                get(Role ofUser (User withId "some_user")).bind().single() `should be equal to`
                        Role(id = "admin", description = "Admin User")

                get(Role withPermission (Permission withId "database.write")).bind().single() `should be equal to`
                        Role(id = "admin", description = "Admin User")

                get(Permission permittedForRole (Role withId "admin")).bind().single() `should be equal to`
                        Permission(id = "database.write")

                get(Permission toResource (Resource withId "database")).bind().single() `should be equal to`
                        Permission(id = "database.write")

                get(Resource resourceToPermission (Permission withId "database.write")).bind().single() `should be equal to`
                        Resource(id = "database")

                get(Permission permittedTo (Action withId "write")).bind().single() `should be equal to`
                        Permission(id = "database.write")

                get(Action permittedBy (Permission withId "database.write")).bind().single() `should be equal to`
                        Action(id = "write")

                Unit
            }
        }
    }

    @BeforeEach
    fun clear() {
        writeTransaction {
            write("MATCH (n) DELETE n;") {

            }
        }
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

        @BeforeAll
        @JvmStatic
        fun start() {
            ConfigRegistry.config = Config()
            Neo4jClient.start()
        }

        @AfterAll
        @JvmStatic
        fun stop() {
            Neo4jClient.stop()
        }
    }
}