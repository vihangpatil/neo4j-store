package dev.vihang.neo4jstore.dsl.model.annotation

import kotlin.annotation.AnnotationRetention.SOURCE
import kotlin.annotation.AnnotationTarget.CLASS

@Retention(SOURCE)
@Target(CLASS)
annotation class Entity

@Retention(SOURCE)
@Target(CLASS)
@Repeatable
annotation class Relation(
    /**
     * Unique relation name in capital case separated by underscore
     */
    val name: String,

    /**
     * Fully qualified name of TO class
     */
    val to: String,

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
