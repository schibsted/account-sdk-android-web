package com.schibsted.account.webflows.util

import retrofit2.Response
import retrofit2.http.GET

interface TestRetrofitApi {
    @GET("username")
    suspend fun getUserName(): Response<TestUser>
}

data class TestUser(
    val username: String?
)