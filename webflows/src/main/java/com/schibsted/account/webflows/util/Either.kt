package com.schibsted.account.webflows.util

/**
 * Represents a value of one of two possible types.
 *
 * An instance of Either is an instance of either `Left` or `Right`.
 * It is right-biased, meaning it's the default case to operate on.
 */

sealed class Either<out L, out R> {
    data class Right<R>(val value: R) : Either<Nothing, R>()
    data class Left<L>(val value: L) : Either<L, Nothing>()

    fun onSuccess(fn: (success: R) -> Unit): Either<L, R> {
        return this.apply { if (this is Right) fn(this.value) }
    }

    fun onFailure(fn: (failure: L) -> Unit): Either<L, R> {
        return this.apply { if (this is Left) fn(this.value) }
    }

    /**
     * Right-biased map.
     */
    fun <T> map(transform: (R) -> T): Either<L, T> {
        return when (this) {
            is Right -> Right(transform(this.value))
            is Left -> this
        }
    }
}
