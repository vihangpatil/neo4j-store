package dev.vihang.neo4jstore.dsl.model.annotation.processor

fun String.snakeToCamelCase() = this.toLowerCase().replace("_[a-z]".toRegex()) {
    it.value.removePrefix("_").capitalize()
}
