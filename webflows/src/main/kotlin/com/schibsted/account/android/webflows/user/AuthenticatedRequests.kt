package com.schibsted.account.android.webflows.user

import android.util.Log
import com.schibsted.account.android.webflows.Logging
import com.schibsted.account.android.webflows.util.Either.Left
import com.schibsted.account.android.webflows.util.Either.Right
import okhttp3.*


internal class AuthenticatedRequestInterceptor(private val user: User) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestWithToken = chain.request().newBuilder()
            .withBearerToken(user.tokens.accessToken)
            .build()
        return chain.proceed(requestWithToken)
    }
}

internal class AccessTokenAuthenticator(private val user: User) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != 401 || response.retryCount >= 1) {
            return null
        }

        val tokenRefreshResult = user.refreshTokens()
        return when (tokenRefreshResult) {
            is Right -> {
                // retry request with fresh access token
                val request = response.request.newBuilder()
                    .withBearerToken(user.tokens.accessToken)
                    .build()
                request
            }
            is Left -> {
                Log.e(Logging.SDK_TAG, "Failed to refresh user tokens: $tokenRefreshResult")
                null
            }
        }
    }
}

private fun Request.Builder.withBearerToken(token: String): Request.Builder = apply {
    header("Authorization", "Bearer $token")
}

private val Response.retryCount: Int
    get() {
        var result = 0
        var currentResponse = priorResponse
        while (currentResponse != null) {
            result++
            currentResponse = currentResponse.priorResponse
        }
        return result
    }
