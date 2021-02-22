package com.schibsted.account.android.webflows.api

sealed class HttpError {
    data class ErrorResponse(val code: Int, val body: String?) : HttpError()
    data class UnexpectedError(val cause: Throwable) : HttpError()
}
