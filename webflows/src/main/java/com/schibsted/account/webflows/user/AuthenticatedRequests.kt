package com.schibsted.account.webflows.user

import android.util.Log
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import com.schibsted.account.webflows.util.Logging
import okhttp3.*

/**
 * OkkHttp interceptor that adds the user access token as a Bearer token in the Authorization
 * header.
 */
internal class AuthenticatedRequestInterceptor(private val user: User) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requestWithToken = chain.request().newBuilder()
            .withBearerToken(user.tokens?.accessToken)
            .build()
        return chain.proceed(requestWithToken)
    }
}

/**
 * OkHttp authenticator that uses the user's refresh token to obtain new user tokens and adds the
 * fresh access token to the request to be retried.
 *
 * It will only try to refresh the tokens once to avoid an infinite loop when the refreshed access
 * token is still not accepted by the server.
 */
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
                    .withBearerToken(user.tokens?.accessToken)
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

private fun Request.Builder.withBearerToken(token: String?): Request.Builder = apply {
    if (token != null) {
        header("Authorization", "Bearer $token")
    } else {
        Log.e(Logging.SDK_TAG, "No access token to include in request")
    }
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
