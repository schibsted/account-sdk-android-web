package com.schibsted.account.android.webflows.api

import android.util.Base64
import com.google.gson.JsonElement
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.URL

class SchibstedAccountAPI(val baseUrl: URL) {


    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.toString())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val schaccService = retrofit.create(SchibstedAccountService::class.java)

    fun makeTokenRequest(
        authCode: String,
        redirectUri: String,
        clientId: String,
        clientSecret: String,
        clientVerifier: String
    ): Unit {
        val request = SchibstedAccountService.Companion.MakeTokenRequest(
            "authorization_code",
            authCode,
            redirectUri,
            clientVerifier
        )
        val authHeader = encodeCredentials(clientId, clientSecret)

        val callback = object : Callback<JsonElement> {
            override fun onFailure(call: Call<JsonElement>, t: Throwable) {
                println("Failed to get token")
            }

            override fun onResponse(call: Call<JsonElement>, response: Response<JsonElement>) {
                print("Success")
                print(response.errorBody()?.string())
            }

        }
        val tokenRequest = schaccService.tokenRequest(
            authHeader,
            request.grant_type,
            request.code,
            request.redirect_uri,
            request.code_verifier
        ).enqueue(callback)
    }

    private fun encodeCredentials(clientId: String, clientSecret: String): String {
        val creds = "$clientId:$clientSecret"
        val value = Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
        return "Basic $value"
    }

}

interface SchibstedAccountService {

    @POST("/oauth/token")
    @FormUrlEncoded
    fun tokenRequest(
        @Header("Authorization") authHeader: String,
        @Field("grant_type") grant_type: String,
        @Field("code") code: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") code_verifier: String
    ): Call<JsonElement>

    @POST("/api/2/oauth/exchange")
    fun oauthExchange(): Call<JsonElement>

    @GET("/api/2/user/{userId}")
    fun userProfile(@Path("userId") userId: String): Call<JsonElement>

    companion object {
        data class MakeTokenRequest(
            val grant_type: String,
            val code: String,
            val redirect_uri: String,
            val code_verifier: String
        )
    }


}

