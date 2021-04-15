package com.schibsted.account.webflows.api

import com.nimbusds.jose.jwk.JWKSet
import retrofit2.Call
import retrofit2.http.*

internal interface SchibstedAccountService {
    @Headers("X-OIDC: v1")
    @FormUrlEncoded
    @POST("/oauth/token")
    suspend fun tokenRequest(@FieldMap params: Map<String, String>): UserTokenResponse

    @GET("/oauth/jwks")
    suspend fun jwks(): JWKSet
}

internal interface SchibstedAccountTokenProtectedService {
    data class SchibstedAccountApiResponse<T>(val data: T)

    @GET("/api/2/user/{userId}")
    suspend fun userProfile(@Path("userId") userId: String): SchibstedAccountApiResponse<UserProfileResponse>

    @FormUrlEncoded
    @POST("/api/2/oauth/exchange")
    suspend fun sessionExchange(
        @Field("clientId") clientId: String,
        @Field("redirectUri") redirectUri: String,
        @Field("type") type: String = "session",
    ): SchibstedAccountApiResponse<SessionExchangeResponse>
}
