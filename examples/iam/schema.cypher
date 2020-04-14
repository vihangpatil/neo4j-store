CREATE (:Identity {label:"Identity"});
CREATE (:User {label:"User"});
CREATE (:Role {label:"Role"});
CREATE (:Permission {label:"Permission"});
CREATE (:Resource {label:"Resource"});
CREATE (:Action {label:"Action"});

MATCH (identity:Identity), (user:User) CREATE (identity)-[:IDENTIFIES]->(user) RETURN identity, user;

MATCH (user:User), (role:Role) CREATE (user)-[:HAS_ROLE]->(role) RETURN user, role;

MATCH (role:Role), (permission:Permission) CREATE (role)-[:HAS_PERMISSION]->(permission) RETURN role, permission;

MATCH (permission:Permission), (resource:Resource) CREATE (permission)-[:PERMISSION_FOR_RESOURCE]->(resource) RETURN permission, resource;

MATCH (permission:Permission), (action:Action) CREATE (permission)-[:PERMITS_ACTION]->(action) RETURN permission, action;