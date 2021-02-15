package com.schibsted.account.android.webflows.api

import com.google.gson.JsonElement
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.net.URL

internal class SchibstedAccountAPI(baseUrl: URL) {
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.toString())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val schaccService = retrofit.create(SchibstedAccountService::class.java)

    fun makeTokenRequest(
        authCode: String,
        redirectUri: String,
        clientId: String,
        codeVerifier: String,
        callback: (Result<UserTokenResponse>) -> Unit
    ) {
        val params = mapOf(
            "client_id" to clientId,
            "grant_type" to "authorization_code",
            "code" to authCode,
            "code_verifier" to codeVerifier,
            "redirect_uri" to redirectUri
        )

        schaccService.tokenRequest(params).enqueue(object : Callback<UserTokenResponse> {
            override fun onFailure(call: Call<UserTokenResponse>, t: Throwable) {
                callback(Result.failure(Error("failed to get user tokens"))) // TODO custom exception
            }

            override fun onResponse(call: Call<UserTokenResponse>, response: Response<UserTokenResponse>) {
                val body = response.body() ?: return callback(Result.failure(Error("no token response")))  // TODO custom exception
                callback(Result.success(body))
            }
        })
    }
}

internal interface SchibstedAccountService {
    @POST("/oauth/token")
    @FormUrlEncoded
    fun tokenRequest(@FieldMap params: Map<String, String>): Call<UserTokenResponse>
}
