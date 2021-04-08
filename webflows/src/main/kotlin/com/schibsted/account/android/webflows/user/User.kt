package com.schibsted.account.android.webflows.user

import android.os.Parcelable
import com.schibsted.account.android.webflows.activities.AuthResultLiveData
import com.schibsted.account.android.webflows.api.ApiResult
import com.schibsted.account.android.webflows.api.UserProfileResponse
import com.schibsted.account.android.webflows.client.Client
import com.schibsted.account.android.webflows.client.RefreshTokenError
import com.schibsted.account.android.webflows.token.UserTokens
import com.schibsted.account.android.webflows.util.BestEffortRunOnceTask
import com.schibsted.account.android.webflows.util.ResultOrError
import kotlinx.parcelize.Parcelize
import okhttp3.*
import java.io.IOException
import java.net.URL


@Parcelize
data class UserSession internal constructor(
    internal val tokens: UserTokens
) : Parcelable

class User {
    private val client: Client
    internal var tokens: UserTokens
    internal val httpClient: OkHttpClient

    private val tokenRefreshTask: BestEffortRunOnceTask<ResultOrError<UserTokens, RefreshTokenError>>

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
        callback: (ResultOrError<Response, Throwable>) -> Unit
    ) {
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(ResultOrError.Failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                callback(ResultOrError.Success(response))
            }
        })
    }

    internal fun refreshTokens(): ResultOrError<UserTokens, RefreshTokenError> {
        val result = tokenRefreshTask.run()
        return result ?: ResultOrError.Failure(RefreshTokenError.ConcurrentRefreshFailure)
    }

    override fun toString(): String {
        return "User(uuid=${tokens.idTokenClaims.sub})"
    }

    override fun equals(other: Any?): Boolean {
        return (other is User) && tokens == other.tokens
    }
}
