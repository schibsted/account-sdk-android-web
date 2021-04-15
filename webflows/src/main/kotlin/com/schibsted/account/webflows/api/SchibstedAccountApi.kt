package com.schibsted.account.webflows.api

import com.google.gson.GsonBuilder
import com.nimbusds.jose.jwk.JWKSet
import com.schibsted.account.webflows.api.SchibstedAccountTokenProtectedService.SchibstedAccountApiResponse
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

typealias ApiResult<T> = Either<HttpError, T>

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

    suspend fun makeTokenRequest(tokenRequest: UserTokenRequest): ApiResult<UserTokenResponse> {
        val params = mapOf(
            "client_id" to tokenRequest.clientId,
            "grant_type" to "authorization_code",
            "code" to tokenRequest.authCode,
            "code_verifier" to tokenRequest.codeVerifier,
            "redirect_uri" to tokenRequest.redirectUri
        )

        return safeApiCall { schaccService.tokenRequest(params) }
    }

    suspend fun makeTokenRequest(tokenRequest: RefreshTokenRequest): ApiResult<UserTokenResponse> {
        val params = mutableMapOf(
            "client_id" to tokenRequest.clientId,
            "grant_type" to "refresh_token",
            "refresh_token" to tokenRequest.refreshToken,
        )

        if (tokenRequest.scope != null) {
            params["scope"] = tokenRequest.scope
        }


        return safeApiCall { schaccService.tokenRequest(params) }
    }

    suspend fun getJwks(): ApiResult<JWKSet> = safeApiCall { schaccService.jwks() }

    suspend fun userProfile(user: User): ApiResult<UserProfileResponse> {
        return proctectedSchaccApi(user) { service ->
            safeApiCall { service.userProfile(user.uuid).data }
        }
    }

    suspend fun sessionExchange(
        user: User,
        clientId: String,
        redirectUri: String
    ): ApiResult<SessionExchangeResponse> {
        return proctectedSchaccApi(user) { service ->
            safeApiCall { service.sessionExchange(clientId, redirectUri).data }
        }
    }

    private suspend fun <T> proctectedSchaccApi(
        user: User,
        block: suspend (SchibstedAccountTokenProtectedService) -> T
    ): T {
        val httpClient = user.httpClient.newBuilder()
            .addInterceptor(SDKUserAgentHeaderInterceptor())
            .build()

        val protectedSchaccService = retrofit.newBuilder()
            .client(httpClient)
            .build()
            .create(SchibstedAccountTokenProtectedService::class.java)
        return block(protectedSchaccService)
    }
}

private suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        Either.Right(apiCall.invoke())
    } catch (throwable: Throwable) {
        when (throwable) {
            is HttpException -> {
                @Suppress("BlockingMethodInNonBlockingContext") // https://github.com/square/retrofit/issues/3255
                val errorBody = throwable.response()?.errorBody()?.string()
                Either.Left(HttpError.ErrorResponse(throwable.code(), errorBody))
            }
            else -> {
                Either.Left(HttpError.UnexpectedError(throwable))
            }
        }
    }
}
