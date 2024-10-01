package com.schibsted.account.webflows.api

import com.nimbusds.jose.jwk.JWKSet
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

internal interface SchibstedAccountService {
    @Headers("X-OIDC: v1")
    @FormUrlEncoded
    @POST("/oauth/token")
    fun tokenRequest(
        @FieldMap params: Map<String, String>,
    ): Call<UserTokenResponse>

    @GET("/oauth/jwks")
    fun jwks(): Call<JWKSet>
}

internal interface SchibstedAccountTokenProtectedService {
    data class SchibstedAccountApiResponse<T>(val data: T)

    @GET("/api/2/user/{userId}")
    fun userProfile(
        @Path("userId") userId: String,
    ): Call<SchibstedAccountApiResponse<UserProfileResponse>>

    @FormUrlEncoded
    @POST("/api/2/oauth/exchange")
    fun sessionExchange(
        @FieldMap params: Map<String, String>,
    ): Call<SchibstedAccountApiResponse<SessionExchangeResponse>>

    @FormUrlEncoded
    @POST("/api/2/oauth/exchange")
    fun codeExchange(
        @Field("clientId") clientId: String,
        @Field("type") type: String = "code",
    ): Call<SchibstedAccountApiResponse<CodeExchangeResponse>>
}
