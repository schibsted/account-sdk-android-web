package com.schibsted.account.webflows.client

import android.util.Base64
import com.schibsted.account.webflows.persistence.StateStorage
import com.schibsted.account.webflows.util.Util
import java.security.MessageDigest

internal class UrlBuilder(
    private val clientConfig: ClientConfiguration,
    private val stateStorage: StateStorage,
    private val authStateKey: String,
) {
    private val defaultScopeValues = setOf("openid", "offline_access")

    fun loginUrl(
        authRequest: AuthRequest,
        state: String? = null,
    ): String {
        val stateValue = state?.let { state } ?: Util.randomString(10)
        val nonce = Util.randomString(10)
        val codeVerifier = Util.randomString(60)

        stateStorage.setValue(
            authStateKey,
            AuthState(stateValue, nonce, codeVerifier, authRequest.mfa),
        )

        val scopes = authRequest.extraScopeValues.union(defaultScopeValues)
        val scopeString = scopes.joinToString(" ")

        val codeChallenge = computeCodeChallenge(codeVerifier)
        val authParams: MutableMap<String, String> =
            mutableMapOf(
                "client_id" to clientConfig.clientId,
                "redirect_uri" to clientConfig.redirectUri,
                "response_type" to "code",
                "state" to stateValue,
                "scope" to scopeString,
                "nonce" to nonce,
                "code_challenge" to codeChallenge,
                "code_challenge_method" to "S256",
            )

        if (authRequest.loginHint != null) {
            authParams["login_hint"] = authRequest.loginHint
        }

        if (authRequest.mfa != null) {
            authParams["acr_values"] = authRequest.mfa.value
        } else {
            authParams["prompt"] = "select_account"
        }

        return "${clientConfig.serverUrl}/oauth/authorize?${Util.queryEncode(authParams)}"
    }

    private fun computeCodeChallenge(codeVerifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(codeVerifier.toByteArray())
        val digest = md.digest()
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
