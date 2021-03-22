package com.schibsted.account.android.webflows.api

import com.schibsted.account.android.webflows.util.ResultOrError
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

internal fun <T> responseToResult(response: Response<T>): ApiResult<T> {
    val body = response.body() ?: return ResultOrError.Failure(
        HttpError.ErrorResponse(response.code(), response.errorBody()?.string())
    )
    return ResultOrError.Success(body)
}

internal class ApiResultCallback<T>(private val callback: (ApiResult<T>) -> Unit) : Callback<T> {
    override fun onFailure(call: Call<T>, t: Throwable) {
        callback(ResultOrError.Failure(HttpError.UnexpectedError(t)))
    }

    override fun onResponse(call: Call<T>, response: Response<T>) {
        callback(responseToResult(response))
    }
}
