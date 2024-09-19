package com.schibsted.account.webflows.api

import com.google.gson.GsonBuilder
import com.nimbusds.jose.jwk.JWKSet
import com.schibsted.account.webflows.api.SchibstedAccountTokenProtectedService.SchibstedAccountApiResponse
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

typealias ApiResult<T> = Either<HttpError, T>

private fun <T> ApiResult<SchibstedAccountApiResponse<T>>.unpack(): ApiResult<T> {
    return this.map { it.data }
}

internal class SchibstedAccountApi(baseUrl: HttpUrl, okHttpClient: OkHttpClient) {
    private val retrofit: Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl.toString())
            .addConverterFactory(createGsonConverterFactory())
            .client(apiHttpClient(okHttpClient.newBuilder()))
            .build()

    private fun createGsonConverterFactory(): GsonConverterFactory {
        val gson =
            GsonBuilder()
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
        callback: (ApiResult<UserTokenResponse>) -> Unit,
    ) {
        val params =
            mutableMapOf(
                "client_id" to tokenRequest.clientId,
                "grant_type" to "authorization_code",
                "code" to tokenRequest.authCode,
                "redirect_uri" to tokenRequest.redirectUri,
            )
        tokenRequest.codeVerifier?.let { codeVerifier ->
            params["code_verifier"] = codeVerifier
        }

        schaccService.tokenRequest(params).enqueue(ApiResultCallback(callback))
    }

    fun makeTokenRequest(tokenRequest: RefreshTokenRequest): ApiResult<UserTokenResponse> {
        val params =
            mutableMapOf(
                "client_id" to tokenRequest.clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to tokenRequest.refreshToken,
            )
        tokenRequest.scope?.let { scope ->
            params["scope"] = scope
        }

        return try {
            responseToResult(schaccService.tokenRequest(params).execute())
        } catch (e: IOException) {
            Left(HttpError.UnexpectedError(e))
        }
    }

    fun getJwks(callback: (ApiResult<JWKSet>) -> Unit) {
        schaccService.jwks().enqueue(ApiResultCallback(callback))
    }

    fun userProfile(
        user: User,
        callback: (ApiResult<UserProfileResponse>) -> Unit,
    ) {
        proctectedSchaccApi(user) { service ->
            service.userProfile(user.uuid)
                .enqueue(ApiResultCallback { callback(it.unpack()) })
        }
    }

    fun sessionExchange(
        user: User,
        clientId: String,
        redirectUri: String,
        state: String?,
        callback: (ApiResult<SessionExchangeResponse>) -> Unit,
    ) {
        val params =
            mutableMapOf(
                "type" to "session",
                "clientId" to clientId,
                "redirectUri" to redirectUri,
            )
        state?.let {
            params["state"] = it
        }

        proctectedSchaccApi(user) { service ->
            service.sessionExchange(params)
                .enqueue(ApiResultCallback { callback(it.unpack()) })
        }
    }

    fun codeExchange(
        user: User,
        clientId: String,
        callback: (ApiResult<CodeExchangeResponse>) -> Unit,
    ) {
        proctectedSchaccApi(user) { service ->
            service.codeExchange(clientId)
                .enqueue(ApiResultCallback { callback(it.unpack()) })
        }
    }

    private fun proctectedSchaccApi(
        user: User,
        block: (SchibstedAccountTokenProtectedService) -> Unit,
    ) {
        val httpClient =
            user.httpClient.newBuilder()
                .addInterceptor(SDKUserAgentHeaderInterceptor())
                .build()

        val protectedSchaccService =
            retrofit.newBuilder()
                .client(httpClient)
                .build()
                .create(SchibstedAccountTokenProtectedService::class.java)
        block(protectedSchaccService)
    }
}
