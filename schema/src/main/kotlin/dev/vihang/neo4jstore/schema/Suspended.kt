package dev.vihang.neo4jstore.schema

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import arrow.core.EitherOf
import arrow.core.fix

/**
 * Binds the given function across [Either.Right].
 *
 * @param f The function to bind across [Either.Right].
 */
suspend fun <A, B, C> EitherOf<A, B>.flatMapSuspend(f: suspend (B) -> Either<A, C>): Either<A, C> =
        fix().let {
            when (it) {
                is Right -> f(it.b)
                is Left -> it
            }
        }