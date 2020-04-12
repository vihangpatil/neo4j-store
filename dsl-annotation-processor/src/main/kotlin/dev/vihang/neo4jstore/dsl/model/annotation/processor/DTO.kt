package dev.vihang.neo4jstore.dsl.model.annotation.processor

data class ClassInfo(val className: String, val packageName: String) {
        constructor(qualifiedName: String) : this(
                className = qualifiedName.substringAfterLast("."),
                packageName = qualifiedName.substringBeforeLast(".")
        )
}

data class RelationInfo(
        val name: String,
        val from: ClassInfo,
        val to: ClassInfo,

        /**
         * FROM forwardRelation TO
         */
        val forwardRelation: String,

        /**
         * TO reverseRelation FROM
         */

        val reverseRelation: String,

        /**
         * get FROM forwardQuery TO
         */
        val forwardQuery: String,

        /**
         * get TO reverseQuery FROM
         */
        val reverseQuery: String
)
