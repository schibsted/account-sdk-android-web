package com.schibsted.account.webflows.persistence

sealed class StorageError {
    data class UnexpectedError(val cause: Throwable) : StorageError()
}
