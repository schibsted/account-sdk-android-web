package com.schibsted.account.webflows.user

import android.os.Parcelable
import com.schibsted.account.webflows.activities.AuthResultLiveData
import com.schibsted.account.webflows.api.ApiResult
import com.schibsted.account.webflows.api.UserProfileResponse
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.RefreshTokenError
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.util.BestEffortRunOnceTask
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import kotlinx.parcelize.Parcelize
import okhttp3.*
import java.io.IOException
import java.net.URL


@Parcelize
data class UserSession internal constructor(
    internal val tokens: UserTokens
) : Parcelable

private typealias TokenRefreshResult = Either<RefreshTokenError, UserTokens>

/** Representation of logged-in user. */
class User {
    private val client: Client
    internal var tokens: UserTokens
    internal val httpClient: OkHttpClient

    private val tokenRefreshTask: BestEffortRunOnceTask<TokenRefreshResult>

    val session: UserSession
        get() {
            return UserSession(tokens)
        }

    /** User integer id (as string) */
    val userId: String
        get() {
            return tokens.idTokenClaims.userId
        }

    /** User UUID */
    val uuid: String
        get() {
            return tokens.idTokenClaims.sub
        }

    constructor(client: Client, session: UserSession) : this(client, session.tokens)

    internal constructor(client: Client, tokens: UserTokens) {
        this.client = client
        this.tokens = tokens
        this.httpClient = client.httpClient.newBuilder()
            .addInterceptor(AuthenticatedRequestInterceptor(this))
            .authenticator(AccessTokenAuthenticator(this))
            .build()
        this.tokenRefreshTask = BestEffortRunOnceTask {
            client.refreshTokensForUser(this)
        }
    }

    /**
     * Log user out.
     *
     * Will remove stored session, including all user tokens, and update [AuthResultLiveData] if
     * configured (via [com.schibsted.account.webflows.activities.AuthorizationManagementActivity.setup]).
     */
    fun logout() {
        client.destroySession()
        AuthResultLiveData.getIfInitialised()?.logout()
    }

    /** Fetch user profile data. */
    fun fetchProfileData(callback: (ApiResult<UserProfileResponse>) -> Unit) {
        client.schibstedAccountApi.userProfile(this, callback)
    }

    /**
     * Generate URL with embedded one-time code for creating a web session for the current user.
     *
     * @param clientId: which client to get the code on behalf of, e.g. client id for associated web application
     * @param redirectUri: where to redirect the user after the session has been created
     * @param callback: callback that receives the URL or an error in case of failure
     */
    fun webSessionUrl(clientId: String, redirectUri: String, callback: (ApiResult<URL>) -> Unit) {
        client.schibstedAccountApi.sessionExchange(this, clientId, redirectUri) {
            val result = it.map {
                client.configuration.serverUrl
                    .toURI()
                    .resolve("/session/${it.code}")
                    .toURL()
            }
            callback(result)
        }
    }

    /**
     * Perform the given [request] with user access token as Bearer token in Authorization header.
     *
     * If the initial request fails with a 401, a refresh token request is made to get a new access
     * token and the request will be retried with the new token if successful.
     *
     * @param request: request to perform with authentication using user tokens
     * @param callback: callback that receives the HTTP response or an error in case of failure
     */
    fun makeAuthenticatedRequest(
        request: Request,
        callback: (Either<Throwable, Response>) -> Unit
    ) {
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Left(e))
            }

            override fun onResponse(call: Call, response: Response) {
                callback(Right(response))
            }
        })
    }

    internal fun refreshTokens(): TokenRefreshResult {
        val result = tokenRefreshTask.run()
        return result ?: Left(RefreshTokenError.ConcurrentRefreshFailure)
    }

    override fun toString(): String {
        return "User(uuid=${tokens.idTokenClaims.sub})"
    }

    override fun equals(other: Any?): Boolean {
        return (other is User) && tokens == other.tokens
    }
}
