package com.schibsted.account.android.webflows.client

import android.content.Context
import android.util.Base64
import com.schibsted.account.android.webflows.persistance.WebViewData
import com.schibsted.account.android.webflows.persistance.WebViewDataStorage
import java.net.URL
import java.security.MessageDigest
import kotlin.random.Random

class Client {
    private val clientConfiguration: ClientConfiguration
    private val storage: WebViewDataStorage

    constructor (context: Context, clientConfiguration: ClientConfiguration) : this(
        clientConfiguration,
        WebViewDataStorage(context.applicationContext)
    )

    internal constructor (clientConfiguration: ClientConfiguration, storage: WebViewDataStorage) {
        this.clientConfiguration = clientConfiguration
        this.storage = storage
    }

    fun generateLoginUrl(mfa: MfaType? = null, extraScopeValues: Set<String> = setOf()): String {
        val state = randomString(10)
        val nonce = randomString(10)
        val codeVerifier = randomString(10)

        // Put those three into storage
        storage.store(
            WebViewData(
                state, nonce, codeVerifier, mfa
            )
        )

        val scopes = extraScopeValues.union(setOf("openid", "offline_access"))
        val scopeString = scopes.joinToString(" ")

        val authParams: MutableMap<String, String> = mutableMapOf(
            "client_id" to clientConfiguration.clientId,
            "redirect_uri" to clientConfiguration.redirectUri,
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

        return "${clientConfiguration.serverUrl}/oauth/authorize?${parseParamsToUrl(authParams)}"
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