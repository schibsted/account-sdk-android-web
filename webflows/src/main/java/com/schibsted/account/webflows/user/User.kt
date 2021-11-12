package com.schibsted.account.webflows.user

import android.os.Parcelable
import com.schibsted.account.webflows.activities.AuthResultLiveData
import com.schibsted.account.webflows.api.ApiResult
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.UserProfileResponse
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.OAuthError
import com.schibsted.account.webflows.client.RefreshTokenError
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.util.BestEffortRunOnceTask
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import kotlinx.parcelize.Parcelize
import okhttp3.*
import timber.log.Timber
import java.io.IOException
import java.net.URL

/** Representation of logged-in user. */
class User {
    // Use the `onlyIfLoggedIn` helper closure for methods that should only be
    // available when `User` is logged-in!

    private val client: Client
    internal var tokens: UserTokens?
    internal val httpClient: OkHttpClient

    private val tokenRefreshTask: BestEffortRunOnceTask<TokenRefreshResult>

    val session: UserSession
        get() = onlyIfLoggedIn { tokens ->
            UserSession(tokens)
        }

    /** User integer id (as string) */
    val userId: String
        get() = onlyIfLoggedIn { tokens ->
            tokens.idTokenClaims.userId
        }

    /** User UUID */
    val uuid: String
        get() = onlyIfLoggedIn { tokens ->
            tokens.idTokenClaims.sub
        }

    constructor(client: Client, session: UserSession) : this(client, session.tokens)

    internal constructor(client: Client, tokens: UserTokens) {
        this.client = client
        this.tokens = tokens
        this.httpClient = client.httpClient.newBuilder()
            .addInterceptor(AuthenticatedRequestInterceptor(this))
            .authenticator(AccessTokenAuthenticator(this))
            .build()
        this.tokenRefreshTask = BestEffortRunOnceTask(5000) {
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
        tokens = null
        client.destroySession()
        AuthResultLiveData.getIfInitialised()?.logout()
    }

    /**
     * Check if this User is logged-in.
     *
     * The user may have been logged out either explicitly via [logout] or automatically if no valid
     * tokens could be obtained (e.g. due to expired or invalidated refresh token).
     */
    fun isLoggedIn(): Boolean = tokens != null

    /** Fetch user profile data. */
    fun fetchProfileData(callback: (ApiResult<UserProfileResponse>) -> Unit) = onlyIfLoggedIn {
        client.schibstedAccountApi.userProfile(this, callback)
    }

    /**
     * Generate URL with embedded one-time code for creating a web session for the current user.
     *
     * @param clientId which client to get the code on behalf of, e.g. client id for associated web application
     * @param redirectUri where to redirect the user after the session has been created
     * @param state An opaque value used by the client to maintain state between the request and callback. It's also recommended to prevent CSRF {@link https://tools.ietf.org/html/rfc6749#section-10.12}
     * @param callback callback that receives the URL or an error in case of failure
     */
    fun webSessionUrl(
        clientId: String,
        redirectUri: String,
        state: String? = null,
        callback: (ApiResult<URL>) -> Unit
    ) =
        onlyIfLoggedIn {
            client.schibstedAccountApi.sessionExchange(
                user = this,
                clientId = clientId,
                redirectUri = redirectUri,
                state = state
            ) {
                val result = it.map { schibstedAccountUrl("/session/${it.code}") }
                callback(result)
            }
        }

    /**
     * Requests a OAuth authorization code for the current user.
     *
     * The code is short-lived and one-time use only.
     * @param clientId which client to get the code on behalf of, e.g. client id for associated web application
     * @param callback callback that receives the one time code
     */
    fun oneTimeCode(clientId: String, callback: (ApiResult<String>) -> Unit) =
        onlyIfLoggedIn {
            client.schibstedAccountApi.codeExchange(this, clientId) {
                callback(it.map { it.code })
            }
        }

    /**
     * Generate URL for Schibsted account pages.
     */
    fun accountPagesUrl(): URL = onlyIfLoggedIn {
        schibstedAccountUrl("/account/summary")
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
     * @param request request to perform with authentication using user tokens
     * @param callback callback that receives the HTTP response or an error in case of failure
     */
    fun makeAuthenticatedRequest(
        request: Request,
        callback: (Either<Throwable, Response>) -> Unit
    ) = onlyIfLoggedIn {
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
        fun shouldLogout(result: TokenRefreshResult?): Boolean {
            return result is Left &&
                    result.value is RefreshTokenError.RefreshRequestFailed &&
                    result.value.error is HttpError.ErrorResponse &&
                    result.value.error.body != null &&
                    OAuthError.fromJson(result.value.error.body).error == "invalid_grant"
        }

        if (shouldLogout(result)) {
            Timber.i("Invalid refresh token, logging user out")
            logout()
            return Left(RefreshTokenError.UserWasLoggedOut)
        }
        return result ?: Left(RefreshTokenError.ConcurrentRefreshFailure)
    }

    override fun toString(): String {
        val uuid = tokens?.idTokenClaims?.sub
        if (uuid != null) {
            return "User(uuid=$uuid)"
        }

        return "User(logged-out)"
    }

    override fun equals(other: Any?): Boolean {
        return (other is User) && tokens == other.tokens
    }

    private fun <T> onlyIfLoggedIn(block: (UserTokens) -> T): T {
        val currentTokens = tokens
            ?: throw IllegalStateException("Can not use tokens of logged-out user!")

        return block(currentTokens)
    }

    private fun schibstedAccountUrl(path: String): URL {
        return client.configuration.serverUrl
            .toURI()
            .resolve(path)
            .toURL()
    }
}

@Parcelize
data class UserSession internal constructor(
    internal val tokens: UserTokens
) : Parcelable

private typealias TokenRefreshResult = Either<RefreshTokenError, UserTokens>
