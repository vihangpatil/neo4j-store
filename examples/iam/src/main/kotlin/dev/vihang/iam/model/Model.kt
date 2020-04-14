package dev.vihang.iam.model

import dev.vihang.neo4jstore.dsl.model.annotation.Entity
import dev.vihang.neo4jstore.dsl.model.annotation.Relation
import dev.vihang.neo4jstore.dsl.model.annotation.Relations
import dev.vihang.neo4jstore.model.HasId

@Entity
@Relation(
        name = "IDENTIFIES", // Identity IDENTIFIES User
        to = "dev.vihang.iam.model.User",
        forwardRelation = "identifies", // Identity identifies User
        reverseRelation = "hasIdentity", // User hasIdentity Identity
        forwardQuery = "ofUser", // get(Identity ofUser [User])
        reverseQuery = "withIdentity"  // get(User withIdentity [Identity])
)
data class Identity(
        override val id: String,
        val type: String,
        val provider: String
) : HasId {
    companion object
}

@Entity
@Relation(
        name = "HAS_ROLE", // User HAS_ROLE Role
        to = "dev.vihang.iam.model.Role",
        forwardRelation = "hasRole", // User hasRole Role
        reverseRelation = "isRoleOf", // Role isRoleOf User
        forwardQuery = "withRole", // get(User withRole [Role])
        reverseQuery = "ofUser" // get(Role ofUser [User])
)
data class User(
        override val id: String
) : HasId {
    companion object
}

@Entity
@Relation(
        name = "HAS_PERMISSION", // Role HAS_PERMISSION Permission
        to = "dev.vihang.iam.model.Permission",
        forwardRelation = "hasPermission", // Role hasPermission Permission
        reverseRelation = "isPermittedFor",  // Permission isPermittedFor Role
        forwardQuery = "withPermission", // get(Role withPermission [Permission])
        reverseQuery = "permittedForRole" // get(Permission permittedForRole [Role])
)
data class Role(
        override val id: String,
        val description: String
) : HasId {
    companion object
}

@Entity
@Relations([
    Relation(
            name = "PERMISSION_FOR_RESOURCE", // Permission FOR_RESOURCE Resource
            to = "dev.vihang.iam.model.Resource",
            forwardRelation = "forResource", // Permission forResource Resource
            reverseRelation = "resourceToPermission", // Resource resourceToPermission Permission
            forwardQuery = "toResource", // get(Permission forResource [Resource])
            reverseQuery = "resourceToPermission" // get(Resource resourceToPermission [Permission])
    ),
    Relation(
        name = "PERMITS_ACTION", // Permission PERMITS_ACTION Action
        to = "dev.vihang.iam.model.Action",
        forwardRelation = "permitsAction", // Permission permitsAction Action
        reverseRelation = "isPermittedBy", // Action isPermittedBy Permission
        forwardQuery = "permittedTo", // get(Permission permittedTo [Action])
        reverseQuery = "permittedBy" // get(Action permittedBy [Permission])
    )
])
data class Permission(
        override val id: String
) : HasId {
    companion object
}

@Entity
data class Resource(
        override val id: String
) : HasId {
    companion object
}

@Entity
data class Action(
        override val id: String
) : HasId {
    companion object
}
