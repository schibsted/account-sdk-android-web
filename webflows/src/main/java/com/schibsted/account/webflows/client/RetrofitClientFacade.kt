package com.schibsted.account.webflows.client

import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import retrofit2.Retrofit

/**  Represents a client registered with Schibsted account - with Retrofit support. */
class RetrofitClientFacade<S>(
    val internalClient: Client,
    private val serviceClass: Class<S>,
    private val retrofitBuilder: Retrofit.Builder,
): ClientInterface by internalClient {

    private var retrofitApi: S? = null
    private var user: User? = null

    /** Resume any previously logged-in user session */
    override fun resumeLastLoggedInUser(): User? {
        val loggedInUser = internalClient.resumeLastLoggedInUser()

        if (loggedInUser != null) {
            user = loggedInUser
            retrofitApi = retrofitBuilder
                .client(loggedInUser.httpClient)
                .build()
                .create(serviceClass)

            return loggedInUser
        }

        user = null
        retrofitApi = null
        return null
    }

    /**
     * Perform the given [request] with user access token as Bearer token in Authorization header.
     *
     * If the initial request fails with a 401, a refresh token request is made to get a new access
     * token and the request will be retried with the new token if successful.
     *
     * If the refresh token request fails with an OAuth 'invalid_grant' error response, meaning the
     * refresh token has expired or been invalidated, the user will be logged-out (and all existing
     * tokens will be destroyed).
     *
     * @param request retrofit suspend request to perform with authentication using user tokens
     */
    suspend fun <V> makeAuthenticatedRequest(request: suspend (S) -> V?): V? {
        if (user?.isLoggedIn() == true) {
            retrofitApi?.let {
                return request(it)
            }
        }
        return null
    }

    internal fun refreshTokensForUser(user: User): Either<RefreshTokenError, UserTokens> {
        return internalClient.refreshTokensForUser(user)
    }
}