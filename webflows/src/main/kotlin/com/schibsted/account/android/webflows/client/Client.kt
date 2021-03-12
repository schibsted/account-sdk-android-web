package com.schibsted.account.android.webflows.client

import android.content.Context
import android.util.Base64
import android.util.Log
import com.schibsted.account.android.webflows.AuthState
import com.schibsted.account.android.webflows.Logging
import com.schibsted.account.android.webflows.MfaType
import com.schibsted.account.android.webflows.api.SchibstedAccountAPI
import com.schibsted.account.android.webflows.persistence.EncryptedSharedPrefsStorage
import com.schibsted.account.android.webflows.persistence.SessionStorage
import com.schibsted.account.android.webflows.persistence.StateStorage
import com.schibsted.account.android.webflows.token.TokenHandler
import com.schibsted.account.android.webflows.user.User
import com.schibsted.account.android.webflows.user.UserSession
import com.schibsted.account.android.webflows.util.ResultOrError
import com.schibsted.account.android.webflows.util.Util
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.util.*

sealed class LoginError {
    object AuthStateReadError : LoginError()
    object UnsolicitedResponse : LoginError()
    data class AuthenticationErrorResponse(val error: String, val errorDescription: String?) :
        LoginError()

    data class TokenErrorResponse(val messsage: String) : LoginError()
    data class UnexpectedError(val message: String) : LoginError()
}
typealias LoginResultHandler = (ResultOrError<User, LoginError>) -> Unit

class Client {
    private val clientConfiguration: ClientConfiguration
    private val tokenHandler: TokenHandler
    private val stateStorage: StateStorage
    private val sessionStorage: SessionStorage

    constructor (
        context: Context,
        clientConfiguration: ClientConfiguration,
        client: OkHttpClient = OkHttpClient.Builder().build()
    ) : this(
        clientConfiguration,
        StateStorage(context.applicationContext),
        EncryptedSharedPrefsStorage(context.applicationContext),
        client,
        TokenHandler(
            clientConfiguration,
            SchibstedAccountAPI(clientConfiguration.serverUrl.toString().toHttpUrl(), client)
        )
    )

    internal constructor (
        clientConfiguration: ClientConfiguration,
        stateStorage: StateStorage,
        sessionStorage: SessionStorage,
        client: OkHttpClient,
        tokenHandler: TokenHandler,
    ) {
        this.clientConfiguration = clientConfiguration
        this.stateStorage = stateStorage
        this.sessionStorage = sessionStorage
        this.tokenHandler = tokenHandler
    }

    fun generateLoginUrl(mfa: MfaType? = null, extraScopeValues: Set<String> = setOf()): String {
        val state = Util.randomString(10)
        val nonce = Util.randomString(10)
        val codeVerifier = Util.randomString(60)

        stateStorage.setValue(AUTH_STATE_KEY, AuthState(state, nonce, codeVerifier, mfa))

        val scopes = extraScopeValues.union(setOf("openid", "offline_access"))
        val scopeString = scopes.joinToString(" ")

        val codeChallenge = computeCodeChallenge(codeVerifier)
        val authParams: MutableMap<String, String> = mutableMapOf(
            "client_id" to clientConfiguration.clientId,
            "redirect_uri" to clientConfiguration.redirectUri,
            "response_type" to "code",
            "state" to state,
            "scope" to scopeString,
            "nonce" to nonce,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256"
        )

        if (mfa != null) {
            authParams["acr_values"] = mfa.value
        } else {
            authParams["prompt"] = "select_account"
        }

        return "${clientConfiguration.serverUrl}/oauth/authorize?${Util.queryEncode(authParams)}"
    }

    private fun computeCodeChallenge(codeVerifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(codeVerifier.toByteArray())
        val digest = md.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun handleAuthenticationResponse(authResponseParameters: String, callback: LoginResultHandler) {
        val authResponse = Util.parseQueryParameters(authResponseParameters)
        val stored = stateStorage.getValue(AUTH_STATE_KEY, AuthState::class)
            ?: return callback(ResultOrError.Failure(LoginError.AuthStateReadError))

        if (stored.state != authResponse["state"]) {
            callback(ResultOrError.Failure(LoginError.UnsolicitedResponse))
            return
        }
        stateStorage.removeValue(AUTH_STATE_KEY)

        val error = authResponse["error"]
        if (error != null) {
            val oauthError =
                LoginError.AuthenticationErrorResponse(error, authResponse["error_description"])
            callback(ResultOrError.Failure(oauthError))
            return
        }

        val authCode = authResponse["code"]
            ?: return callback(ResultOrError.Failure(LoginError.UnexpectedError("Missing authorization code in authentication response")))

        tokenHandler.makeTokenRequest(
            authCode,
            stored
        ) { result ->
            result.onSuccess { tokenResponse ->
                Log.d(Logging.SDK_TAG, "Token response: $tokenResponse")
                val userSession =
                    UserSession(clientConfiguration.clientId, tokenResponse.userTokens, Date())
                sessionStorage.save(userSession)
                callback(ResultOrError.Success(User(this, userSession)))
            }.onFailure { err ->
                Log.d(Logging.SDK_TAG, "Token error response: $err")
                callback(ResultOrError.Failure(LoginError.TokenErrorResponse(err.toString())))
            }
        }
    }

    fun resumeLastLoggedInUser(): User? {
        val session = sessionStorage.get(clientConfiguration.clientId) ?: return null
        return User(this, session)
    }

    fun destroySession() {
        sessionStorage.remove(clientConfiguration.clientId)
    }

    internal companion object {
        const val AUTH_STATE_KEY = "AuthState"
    }
}
