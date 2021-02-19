package com.schibsted.account.android.webflows.util

sealed class ResultOrError<out S, out E> {
    data class Success<S>(val value: S) : ResultOrError<S, Nothing>()
    data class Failure<E>(val error: E) : ResultOrError<Nothing, E>()

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
