package dev.vihang.neo4jstore.transaction

import arrow.core.Either
import dev.vihang.common.logging.getLogger
import dev.vihang.neo4jstore.transaction.ActionType.FINAL
import dev.vihang.neo4jstore.transaction.ActionType.REVERSAL
import org.neo4j.driver.Transaction

class ExtendedTransaction(private val transaction: Transaction) : Transaction by transaction {

    private val logger by getLogger()

    private val reversalActions = mutableListOf<() -> Unit>()
    private val finalActions = mutableListOf<() -> Unit>()

    private var success = true

    private fun toActionList(actionType: ActionType) = when (actionType) {
        REVERSAL -> reversalActions
        FINAL -> finalActions
    }

    private fun doActions(actionType: ActionType) {
        val actions = toActionList(actionType)
        while (actions.isNotEmpty()) {
            actions[0]()
            actions.removeAt(0)
        }
    }

    fun addAction(actionType: ActionType, action: () -> Unit) {
        toActionList(actionType).add(action)
    }

    override fun rollback() {
        success = false
        transaction.rollback()
    }

    override fun close() {
        if (!success) {
            doActions(REVERSAL)
        }
        finalActions.reverse()
        doActions(FINAL)
    }
}

enum class ActionType {
    REVERSAL,
    FINAL,
}

typealias Action<P> = (P) -> Unit

private fun <L, R> Either<L, R>.addAction(
        extendedTransaction: ExtendedTransaction,
        action: Action<R>,
        actionType: ActionType): Either<L, R> {

    this.map { param ->
        extendedTransaction.addAction(actionType) {
            action(param)
        }
    }
    return this
}

fun <L, R> Either<L, R>.linkReversalActionToTransaction(
        extendedTransaction: ExtendedTransaction,
        reversalAction: Action<R>): Either<L, R> = addAction(extendedTransaction, reversalAction, REVERSAL)

fun <L, R> Either<L, R>.finallyDo(
        extendedTransaction: ExtendedTransaction,
        finalAction: Action<R>): Either<L, R> = addAction(extendedTransaction, finalAction, FINAL)

fun <L, R> Either<L, R>.ifFailedThenRollback(extendedTransaction: ExtendedTransaction): Either<L, R> = mapLeft { error ->
    extendedTransaction.rollback()
    error
}