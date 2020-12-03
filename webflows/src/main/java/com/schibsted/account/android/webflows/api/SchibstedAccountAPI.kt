package com.schibsted.account.android.webflows.api

import com.google.gson.JsonElement
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.net.URL

class SchibstedAccountAPI(val baseUrl: URL) {

    private val filter = HttpLoggingInterceptor()
    private val client = OkHttpClient.Builder().addInterceptor(filter).build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val schaccService = retrofit.create(SchibstedAccountService::class.java)

    private val sdkHeaders: Map<String, String> = mapOf(Pair("User-Agent", "temp-fake-agent"))


    fun tokenRequest(authorization: String) {
//        println(schaccService.tokenRequest().)


        println(schaccService.tokenRequest().execute().errorBody()!!.charStream().readText())
    }


}

interface SchibstedAccountService {

    @POST("/oauth/token")
    fun tokenRequest(): Call<JsonElement>

    @POST("/api/2/oauth/exchange")
    fun oauthExchange(): Call<JsonElement>

    @GET("/api/2/user/{userId}")
    fun userProfile(@Path("userId") userId: String): Call<JsonElement>


}

