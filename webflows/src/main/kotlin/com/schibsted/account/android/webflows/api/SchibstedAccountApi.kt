package com.schibsted.account.android.webflows.api

import com.google.gson.GsonBuilder
import com.nimbusds.jose.jwk.JWKSet
import com.schibsted.account.android.webflows.api.SchibstedAccountTokenProtectedService.SchibstedAccountApiResponse
import com.schibsted.account.android.webflows.user.User
import com.schibsted.account.android.webflows.util.ResultOrError
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

typealias ApiResult<T> = ResultOrError<T, HttpError>

private fun <T> ApiResult<SchibstedAccountApiResponse<T>>.unpack(): ApiResult<T> {
    return this.map { it.data }
}

internal class SchibstedAccountApi(baseUrl: HttpUrl, okHttpClient: OkHttpClient) {
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.toString())
        .addConverterFactory(createGsonConverterFactory())
        .client(apiHttpClient(okHttpClient.newBuilder()))
        .build()

    private fun createGsonConverterFactory(): GsonConverterFactory {
        val gson = GsonBuilder()
            .registerTypeAdapter(JWKSet::class.java, JWKSetDeserializer())
            .create()

        return GsonConverterFactory.create(gson)
    }

    private fun apiHttpClient(builder: OkHttpClient.Builder): OkHttpClient {
        return builder
            .addInterceptor(SDKUserAgentHeaderInterceptor())
            .build()
    }

    private val schaccService = retrofit.create(SchibstedAccountService::class.java)

    fun makeTokenRequest(
        tokenRequest: UserTokenRequest,
        callback: (ApiResult<UserTokenResponse>) -> Unit
    ) {
        val params = mapOf(
            "client_id" to tokenRequest.clientId,
            "grant_type" to "authorization_code",
            "code" to tokenRequest.authCode,
            "code_verifier" to tokenRequest.codeVerifier,
            "redirect_uri" to tokenRequest.redirectUri
        )

        schaccService.tokenRequest(params).enqueue(ApiResultCallback(callback))
    }

    fun makeTokenRequest(tokenRequest: RefreshTokenRequest): ApiResult<UserTokenResponse> {
        val params = mutableMapOf(
            "client_id" to tokenRequest.clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to tokenRequest.refreshToken,
        )

        if (tokenRequest.scope != null) {
            params["scope"] = tokenRequest.scope
        }

        return try {
            responseToResult(schaccService.tokenRequest(params).execute())
        } catch (e: IOException) {
            ResultOrError.Failure(HttpError.UnexpectedError(e))
        }
    }

    fun getJwks(callback: (ApiResult<JWKSet>) -> Unit) {
        schaccService.jwks().enqueue(ApiResultCallback(callback))
    }

    fun userProfile(user: User, callback: (ApiResult<UserProfileResponse>) -> Unit) {
        proctectedSchaccApi(user) { service ->
            service.userProfile(user.uuid)
                .enqueue(ApiResultCallback { callback(it.unpack()) })
        }
    }

    private fun proctectedSchaccApi(
        user: User,
        block: (SchibstedAccountTokenProtectedService) -> Unit
    ) {
        val httpClient = user.httpClient.newBuilder()
            .addInterceptor(SDKUserAgentHeaderInterceptor())
            .build()

        val protectedSchaccService = retrofit.newBuilder()
            .client(httpClient)
            .build()
            .create(SchibstedAccountTokenProtectedService::class.java)
        block(protectedSchaccService)
    }
}
