package com.schibsted.account.android.webflows.api

import android.os.Build
import com.google.gson.*
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.shaded.json.parser.ParseException
import com.schibsted.account.android.webflows.BuildConfig
import com.schibsted.account.android.webflows.util.ResultOrError
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.IOException
import java.lang.reflect.Type

private typealias ApiResult<T> = ResultOrError<T, HttpError>

private class SDKUserAgentHeaderInterceptor : Interceptor {
    val userAgentHeaderValue: String = "AccountSDKAndroidWeb/${BuildConfig.VERSION_NAME} " +
            "(Linux; Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}; " +
            "${Build.MANUFACTURER}; ${Build.MODEL})"

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgentHeaderValue)
            .build()
        return chain.proceed(request)
    }
}

internal class SchibstedAccountAPI(baseUrl: HttpUrl, okHttpClient: OkHttpClient) {
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
            ApiResultCallback.responseToResult(schaccService.tokenRequest(params).execute())
        } catch (e: IOException) {
            ResultOrError.Failure(HttpError.UnexpectedError(e))
        }
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
        callback(responseToResult(response))
    }

    companion object {
        fun <T> responseToResult(response: Response<T>): ResultOrError<T, HttpError> {
            val body = response.body() ?: return ResultOrError.Failure(
                HttpError.ErrorResponse(response.code(), response.errorBody()?.string())
            )
            return ResultOrError.Success(body)
        }
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
