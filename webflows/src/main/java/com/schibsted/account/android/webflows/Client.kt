package com.schibsted.account.android.webflows

import android.content.Context
import android.util.Base64
import com.schibsted.account.android.webflows.client.Environment
import com.schibsted.account.android.webflows.client.MfaType
import com.schibsted.account.android.webflows.persistance.WebViewData
import com.schibsted.account.android.webflows.persistance.WebViewDataStorage
import java.security.MessageDigest
import kotlin.random.Random

class Client(val env: Environment, val context: Context) {

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
        val storage = WebViewDataStorage(context)
        storage.store(
            WebViewData(
                state, nonce, codeVerifier, mfa
            )
        )

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

    private fun generateCodeChallenge(codeVerifier: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(codeVerifier.toByteArray())
        val digest = md.digest()
        return Base64.encodeToString(digest, Base64.NO_WRAP)
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