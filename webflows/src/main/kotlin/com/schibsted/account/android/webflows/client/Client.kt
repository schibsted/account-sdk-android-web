package com.schibsted.account.android.webflows.client

import android.content.Context
import android.util.Base64
import android.util.Log
import com.schibsted.account.android.webflows.Logging
import com.schibsted.account.android.webflows.Util
import com.schibsted.account.android.webflows.api.SchibstedAccountAPI
import com.schibsted.account.android.webflows.persistence.StateStorage
import com.schibsted.account.android.webflows.user.User
import okhttp3.OkHttpClient
import java.security.MessageDigest

typealias LoginResultHandler = (Result<User>) -> Unit

class Client {
    private val clientConfiguration: ClientConfiguration
    private val schibstedAccountApi: SchibstedAccountAPI
    private val storage: StateStorage

    constructor (
        context: Context,
        clientConfiguration: ClientConfiguration,
        client: OkHttpClient = OkHttpClient.Builder().build()
    ) : this(
        clientConfiguration,
        StateStorage(context.applicationContext),
        client
    )

    internal constructor (
        clientConfiguration: ClientConfiguration,
        storage: StateStorage,
        client: OkHttpClient
    ) {
        this.clientConfiguration = clientConfiguration
        this.storage = storage
        this.schibstedAccountApi = SchibstedAccountAPI(clientConfiguration.serverUrl, client)
    }

    fun generateLoginUrl(mfa: MfaType? = null, extraScopeValues: Set<String> = setOf()): String {
        val state = Util.randomString(10)
        val nonce = Util.randomString(10)
        val codeVerifier = Util.randomString(60)

        storage.setValue(WEB_FLOW_DATA_KEY, WebFlowData(state, nonce, codeVerifier, mfa))

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
        val stored = storage.getValue<WebFlowData>(WEB_FLOW_DATA_KEY)
            ?: return callback(Result.failure(Error("Failed to read stored data"))) // TODO custom exception

        if (stored.state != authResponse["state"]) {
            callback(Result.failure(Error("Unsolicited response"))) // TODO custom exception
            return
        }
        storage.removeValue(WEB_FLOW_DATA_KEY)

        val error = authResponse["error"]
        if (error != null) {
            callback(Result.failure(Error("OAuth error"))) // TODO custom exception, include description as well
            return
        }

        val authCode = authResponse["code"]
            ?: return callback(Result.failure(Error("No auth code"))) // TODO custom exception

        schibstedAccountApi.makeTokenRequest(
            authCode,
            stored.codeVerifier,
            clientConfiguration.clientId,
            clientConfiguration.redirectUri,
        ) { result ->
            result.map { tokenResponse ->
                Log.d(Logging.SDK_TAG, "Token response: $tokenResponse")
            }.onFailure { err ->
                Log.d(Logging.SDK_TAG, "Token error response: $err")
            }
        }

        // TODO validate ID token, then store tokens
    }

    internal companion object {
        const val WEB_FLOW_DATA_KEY = "WebFlowData"

        data class WebFlowData(
            val state: String,
            val nonce: String,
            val codeVerifier: String,
            val mfa: MfaType?
        )
    }
}
