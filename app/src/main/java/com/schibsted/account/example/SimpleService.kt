package com.schibsted.account.example

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface SimpleService {
    @GET("/api/2/user/{userId}")
    fun userProfile(@Path("userId") userId: String): Call<ResponseBody>
}
