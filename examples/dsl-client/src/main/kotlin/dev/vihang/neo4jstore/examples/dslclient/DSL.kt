package dev.vihang.neo4jstore.examples.dslclient

import dev.vihang.neo4jstore.dsl.model.EntityContext
import dev.vihang.neo4jstore.dsl.model.RelatedFromClause
import dev.vihang.neo4jstore.dsl.model.RelatedToClause
import dev.vihang.neo4jstore.dsl.model.RelationExpression
import dev.vihang.neo4jstore.schema.model.None
import dev.vihang.neo4jstore.schema.model.Relation

val hasRoleRelation: Relation<User, None, Role> = Relation(
        name = "HAS_ROLE",
        from = User::class,
        relation = None::class,
        to = Role::class
)

class UserContext(id: String) : EntityContext<User>(User::class, id)

infix fun User.Companion.withId(id: String) = UserContext(id)

class RoleContext(id: String) : EntityContext<Role>(Role::class, id)

infix fun Role.Companion.withId(id: String) = RoleContext(id)



infix fun User.Companion.withRole(role: RoleContext) : RelatedToClause<User, Role> = RelatedToClause(
        relation = hasRoleRelation,
        toId = role.id
)


infix fun Role.Companion.ofUser(user: UserContext) : RelatedFromClause<User, Role> = RelatedFromClause(
        relation = hasRoleRelation,
        fromId = user.id
)

infix fun UserContext.hasRole(role: RoleContext) : RelationExpression<User, None, Role> = RelationExpression(
        relation = hasRoleRelation,
        fromId = id,
        toId = role.id
)

infix fun RoleContext.ofUser(user: UserContext) : RelationExpression<User, None, Role> = RelationExpression(
        relation = hasRoleRelation,
        fromId = user.id,
        toId = id
)
