package com.schibsted.account.webflows.client

import com.schibsted.account.webflows.persistence.StorageError
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Either
import retrofit2.Retrofit

/**  Represents a client registered with Schibsted account - with Retrofit support. */
class RetrofitClient<S>(
    val internalClient: Client,
    private val serviceClass: Class<S>,
    private val retrofitBuilder: Retrofit.Builder,
) : ClientInterface by internalClient {

    var retrofitApi: S? = null
        private set

    private var user: User? = null

    /** Initiates RetrofitClient and resume any previously logged-in user session
     *
     * This should ideally only be used on app startup
     * User will only be updated with new user if resumedUser contains a new user
     * */
    override fun resumeLastLoggedInUser(callback: (Either<StorageError, User?>) -> Unit) {
        internalClient.resumeLastLoggedInUser { result ->
            result
                .foreach { resumedUser: User? ->
                    when {
                        user?.equals(resumedUser) == true -> {
                            callback(Either.Right(user))
                        }
                        resumedUser != null -> {
                            user = resumedUser
                            retrofitApi = retrofitBuilder
                                .client(resumedUser.httpClient)
                                .build()
                                .create(serviceClass)

                            callback(Either.Right(resumedUser))
                        }
                        else -> {
                            reset()
                            callback(Either.Right(null))
                        }
                    }
                }
                .left().foreach {
                    reset()
                    callback(Either.Left(it))
                }
        }
    }

    /** Check if RetrofitClient has been initialized */
    fun isInitialized(): Boolean {
        user ?: return false
        retrofitApi ?: return false
        return true
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

    private fun reset() {
        user = null
        retrofitApi = null
    }
}
