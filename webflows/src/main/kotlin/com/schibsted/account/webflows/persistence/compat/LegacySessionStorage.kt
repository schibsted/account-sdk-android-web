package com.schibsted.account.webflows.persistence.compat

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.nimbusds.jose.JWSObject
import com.schibsted.account.webflows.token.IdTokenClaims
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.user.StoredUserSession
import java.text.ParseException
import java.util.*

internal data class LegacyUserTokens(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("id_token") val idToken: String? = null
)

internal data class LegacySession(
    val lastActive: Long,
    val userId: String,
    @SerializedName("token") val tokens: LegacyUserTokens
)

/**
 * Storage implementation compatible with previous Schibsted account Android SDK.
 *
 * @see <a href="https://github.com/schibsted/account-sdk-android/blob/master/core/src/main/java/com/schibsted/account/persistence/SessionStorageDelegate.kt" target="_top">Old Schibsted account Android SDK</a>
 */
private class LegacyTokenStorage(context: Context) {
    private var sessions: List<LegacySession> by SessionStorageDelegate(
        context,
        PREFERENCE_FILENAME
    )

    fun get(): Collection<LegacySession> {
        return sessions
    }

    fun remove() {
        sessions = emptyList()
    }

    companion object {
        private const val PREFERENCE_FILENAME = "IDENTITY_PREFERENCES"
    }
}

internal class LegacySessionStorage(context: Context) {
    private val legacyTokenStorage = LegacyTokenStorage(context)

    fun get(clientId: String): StoredUserSession? {
        val sessions = legacyTokenStorage.get()
            .mapNotNull { toStoredUserSession(it) }
            .filter { it.clientId == clientId }

        return sessions.sortedByDescending { it.updatedAt }.firstOrNull() // TODO test this yields the latest
    }

    fun remove() {
        legacyTokenStorage.remove()
    }

    private fun toStoredUserSession(legacySession: LegacySession): StoredUserSession? {
        val accessToken = legacySession.tokens.accessToken
        val clientId = unverifiedClaims(accessToken)?.get("client_id") ?: return null

        val idToken = legacySession.tokens.idToken ?: return null
        val unverifiedIdTokenClaims = unverifiedClaims(idToken) ?: return null
        val sub = unverifiedIdTokenClaims["sub"] ?: return null

        val legacyUserId = unverifiedIdTokenClaims["legacy_user_id"] as? String
        val idTokenClaims = IdTokenClaims(
            iss = unverifiedIdTokenClaims["iss"] as String,
            sub = sub as String,
            userId = legacyUserId ?: "",
            aud = emptyList(),
            exp = unverifiedIdTokenClaims["exp"] as Long,
            nonce = unverifiedIdTokenClaims["nonce"] as? String,
            amr = null
        )
        val userTokens = UserTokens(
            legacySession.tokens.accessToken,
            legacySession.tokens.refreshToken,
            legacySession.tokens.idToken,
            idTokenClaims
        )

        return StoredUserSession(clientId as String, userTokens, Date(legacySession.lastActive))
    }

    private fun unverifiedClaims(token: String): Map<String, Any>? {
        try {
            val jws = JWSObject.parse(token)
            return jws.payload.toJSONObject()
        } catch (e: ParseException) {
            return null
        }
    }
}