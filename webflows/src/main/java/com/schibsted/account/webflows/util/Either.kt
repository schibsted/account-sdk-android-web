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

    data class LeftProjection<out L, out R>(private val either: Either<L, R>) {
        /**
         * The given function is applied if this is a `Left`.
         */
        fun <V> map(transform: (L) -> V): Either<V, R> {
            return when (either) {
                is Left -> Left(transform(either.value))
                is Right -> either
            }
        }

        /**
         * The given side-effecting function is applied if this is a `Left`.
         */
        fun foreach(block: (L) -> Unit): LeftProjection<L, R> {
            when (either) {
                is Left -> block(either.value)
                else -> Unit // do nothing
            }

            return this
        }
    }

    /**
     * The given function is applied if this is a `Right`.
     */
    fun <V> map(transform: (R) -> V): Either<L, V> {
        return when (this) {
            is Right -> Right(transform(value))
            is Left -> Left(value)
        }
    }

    /**
     * The given side-effecting function is applied if this is a `Right`.
     */
    fun foreach(block: (R) -> Unit): Either<L, R> {
        when (this) {
            is Right -> block(value)
            else -> Unit // do nothing
        }

        return this
    }

    fun left(): LeftProjection<L, R> = LeftProjection(this)
}
