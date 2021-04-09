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

class User {
    private val client: Client
    internal var tokens: UserTokens
    internal val httpClient: OkHttpClient

    private val tokenRefreshTask: BestEffortRunOnceTask<TokenRefreshResult>

    val session: UserSession
        get() {
            return UserSession(tokens)
        }
    val userId: String
        get() {
            return tokens.idTokenClaims.userId
        }
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

    fun logout() {
        client.destroySession()
        AuthResultLiveData.getIfInitialised()?.logout()
    }

    fun fetchProfileData(callback: (ApiResult<UserProfileResponse>) -> Unit) {
        client.schibstedAccountApi.userProfile(this, callback)
    }

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
