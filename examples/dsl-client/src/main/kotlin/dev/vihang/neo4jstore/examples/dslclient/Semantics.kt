package dev.vihang.neo4jstore.examples.dslclient

import dev.vihang.neo4jstore.dsl.EntityContext
import dev.vihang.neo4jstore.dsl.RelatedFromClause
import dev.vihang.neo4jstore.dsl.RelatedToClause
import dev.vihang.neo4jstore.dsl.RelationExpression
import dev.vihang.neo4jstore.examples.model.Role
import dev.vihang.neo4jstore.examples.model.User
import dev.vihang.neo4jstore.examples.model.hasRoleRelation
import dev.vihang.neo4jstore.schema.EntityType
import dev.vihang.neo4jstore.schema.None
import dev.vihang.neo4jstore.schema.RelationType

// using entity classes in `examples/model` project, create [EntityType]
val userEntityType = EntityType(User::class.java)
val roleEntityType = EntityType(Role::class.java)

val hasRoleType = RelationType(
        relation = hasRoleRelation,
        from = userEntityType,
        to = roleEntityType,
        dataClass = None::class.java
)

infix fun User.Companion.withId(id: String) = UserContext(id)

infix fun Role.Companion.withId(id: String) = RoleContext(id)

class UserContext(id: String) : EntityContext<User>(User::class, id) {
    infix fun hasRole(role: RoleContext) : RelationExpression<User, None, Role> = RelationExpression(
            relationType = hasRoleType,
            fromId = id,
            toId = role.id
    )
}

class RoleContext(id: String) : EntityContext<Role>(Role::class, id) {
    infix fun hasRole(role: RoleContext) : RelationExpression<User, None, Role> = RelationExpression(
            relationType = hasRoleType,
            fromId = id,
            toId = role.id
    )
}

infix fun User.Companion.withRole(role: RoleContext) : RelatedToClause<User, Role> = RelatedToClause(
        relationType = hasRoleType,
        toId = role.id
)

infix fun Role.Companion.ofUser(user: UserContext) : RelatedFromClause<User, Role> = RelatedFromClause(
        relationType = hasRoleType,
        fromId = user.id
)