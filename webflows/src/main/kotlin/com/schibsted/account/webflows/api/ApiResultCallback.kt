package com.schibsted.account.webflows.api

import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal fun <T> responseToResult(response: Response<T>): ApiResult<T> {
    val body = response.body() ?: return Left(
        HttpError.ErrorResponse(response.code(), response.errorBody()?.string())
    )
    return Right(body)
}

internal class ApiResultCallback<T>(private val callback: (ApiResult<T>) -> Unit) : Callback<T> {
    override fun onFailure(call: Call<T>, t: Throwable) {
        callback(Left(HttpError.UnexpectedError(t)))
    }

    override fun onResponse(call: Call<T>, response: Response<T>) {
        callback(responseToResult(response))
    }
}
