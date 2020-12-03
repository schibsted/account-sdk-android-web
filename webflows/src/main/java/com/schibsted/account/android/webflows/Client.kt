package com.schibsted.account.android.webflows

import com.schibsted.account.android.webflows.client.Environment
import com.schibsted.account.android.webflows.client.MfaType
import kotlin.random.Random

class Client(val env: Environment) {

    fun generateLoginUrl(
        clientId: String,
        redirectUri: String,
        mfa: MfaType?,
        extraScopeValues: Set<String>
    ): String {
        val state = randomString(10)
        val nonce = randomString(10)
        val codeVerifier = randomString(10)

        // Put those three into storage

        val scopes: Set<String> = extraScopeValues.union(listOf("openid"))
        val scopeString = scopes.joinToString(" ")

        val authParams: MutableMap<String, String> = mutableMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "state" to state,
            "scope" to scopeString,
            "nonce" to nonce,
            "code_challenge" to generateCodeChallenge(codeVerifier),
            "code_challenge_method" to "S256"
        )

        if (mfa != null) {
            authParams["acr_values"] = mfa.value
        } else {
            authParams["prompt"] = "select_account"
        }

        return "${env.url}/oauth/authorize?${parseParamsToUrl(authParams)}"
    }

    private fun parseParamsToUrl(params: Map<String, String>): String {
        fun String.utf8(): String = java.net.URLEncoder.encode(this, "UTF-8")
        return params.map { (k: String, v: String) -> "${k.utf8()}=${v.utf8()}" }.joinToString("&")
    }

    fun generateCodeChallenge(value: String): String {
        //TODO: Prepare it according to https://www.oauth.com/oauth2-servers/pkce/authorization-request/
        return "fakeCodeChallange";
    }

    private fun randomString(length: Int): String {
        val letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val result =
            (1..length).map {
                Random.nextInt(letters.length)
            }.map(letters::get)
                .joinToString("")

        return result;
    }
}