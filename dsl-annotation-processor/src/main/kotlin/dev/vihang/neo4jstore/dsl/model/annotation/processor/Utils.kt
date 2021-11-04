package dev.vihang.neo4jstore.dsl.model.annotation.processor

import java.util.*

fun String.snakeToCamelCase() = this.lowercase().replace("_[a-z]".toRegex()) { result ->
    result.value.removePrefix("_")
        .replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
}
