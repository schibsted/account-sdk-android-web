package com.schibsted.account.android.webflows.api

import com.google.gson.*
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.shaded.json.parser.ParseException
import com.schibsted.account.android.webflows.util.ResultOrError
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.lang.reflect.Type

private typealias ApiResult<T> = ResultOrError<T, HttpError>

internal class SchibstedAccountAPI(baseUrl: HttpUrl, okHttpClient: OkHttpClient) {
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.toString())
        .addConverterFactory(createGsonConverterFactory())
        .client(okHttpClient)
        .build()

    private fun createGsonConverterFactory(): GsonConverterFactory {
        val gson = GsonBuilder()
            .registerTypeAdapter(JWKSet::class.java, JWKSetDeserializer())
            .create()

        return GsonConverterFactory.create(gson)
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

    fun getJwks(callback: (ApiResult<JWKSet>) -> Unit) {
        schaccService.jwks().enqueue(ApiResultCallback(callback))
    }
}

private class ApiResultCallback<T>(private val callback: (ApiResult<T>) -> Unit) : Callback<T> {
    override fun onFailure(call: Call<T>, t: Throwable) {
        callback(ResultOrError.Failure(HttpError.UnexpectedError(t)))
    }

    override fun onResponse(call: Call<T>, response: Response<T>) {
        val body = response.body()
            ?: return callback(
                ResultOrError.Failure(
                    HttpError.ErrorResponse(
                        response.code(), response.errorBody()?.string()
                    )
                )
            )
        callback(ResultOrError.Success(body))
    }
}

private interface SchibstedAccountService {
    @Headers("X-OIDC: v1")
    @FormUrlEncoded
    @POST("/oauth/token")
    fun tokenRequest(@FieldMap params: Map<String, String>): Call<UserTokenResponse>

    @GET("/oauth/jwks")
    fun jwks(): Call<JWKSet>
}

private class JWKSetDeserializer : JsonDeserializer<JWKSet> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): JWKSet {
        try {
            return JWKSet.parse(json.toString())
        } catch (e: ParseException) {
            throw JsonParseException(e)
        }
    }
}
