package com.schibsted.account.android.webflows.util

sealed class ResultOrError<out S, out E> {
    data class Success<S>(val value: S) : ResultOrError<S, Nothing>()
    data class Failure<E>(val error: E) : ResultOrError<Nothing, E>()

    fun <V> map(transform: (S) -> V): ResultOrError<V, E> {
        return when (this) {
            is Success -> Success(transform(value))
            is Failure -> Failure(this.error)
        }
    }

    fun onSuccess(block: (S) -> Unit): ResultOrError<S, E> {
        if (this is Success) {
            block(value)
        }

        return this
    }

    fun onFailure(block: (E) -> Unit): ResultOrError<S, E> {
        if (this is Failure) {
            block(error)
        }

        return this
    }
}
