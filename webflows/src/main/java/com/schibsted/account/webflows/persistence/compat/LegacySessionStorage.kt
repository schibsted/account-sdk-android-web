package com.schibsted.account.webflows.persistence.compat

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.nimbusds.jose.JWSObject
import com.schibsted.account.webflows.token.IdTokenClaims
import com.schibsted.account.webflows.token.MigrationUserTokens
import com.schibsted.account.webflows.user.MigrationStoredUserSession
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
    val token: LegacyUserTokens
)

/**
 * Storage implementation compatible with previous Schibsted account Android SDK.
 *
 * @see <a href="https://github.com/schibsted/account-sdk-android/blob/master/core/src/main/java/com/schibsted/account/persistence/SessionStorageDelegate.kt" target="_top">Old Schibsted account Android SDK</a>
 */
internal class LegacyTokenStorage(context: Context, legacySharedPrefsFilename: String?) {
    private var sessions: List<LegacySession> by SessionStorageDelegate(
        context,
        legacySharedPrefsFilename ?: PREFERENCE_FILENAME
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

internal class LegacySessionStorage(private val legacyTokenStorage: LegacyTokenStorage) {
    internal constructor(context: Context, legacySharedPrefsFilename: String?) : this(LegacyTokenStorage(context, legacySharedPrefsFilename))

    fun get(clientId: String): MigrationStoredUserSession? {
        val sessions = legacyTokenStorage.get()
            .mapNotNull { toStoredUserSession(it) }
            .filter { it.clientId == clientId }

        return sessions.maxByOrNull { it.updatedAt }
    }

    fun remove() {
        legacyTokenStorage.remove()
    }

    private fun toStoredUserSession(legacySession: LegacySession): MigrationStoredUserSession? {
        val accessToken = legacySession.token.accessToken
        val clientId = unverifiedClaims(accessToken)?.get("client_id") ?: return null
        val refreshToken = legacySession.token.refreshToken ?: return null

        val idToken = legacySession.token.idToken
        val userTokens = MigrationUserTokens(
            accessToken = legacySession.token.accessToken,
            refreshToken = refreshToken,
            idToken = idToken,
            idTokenClaims = createIdTokenClaims(idToken)
        )

        return MigrationStoredUserSession(
            clientId = clientId as String,
            userTokens = userTokens,
            updatedAt = Date(legacySession.lastActive)
        )
    }

    private fun createIdTokenClaims(idToken: String?): IdTokenClaims? {
        idToken ?: return null
        val unverifiedIdTokenClaims = unverifiedClaims(idToken) ?: return null
        val sub = unverifiedIdTokenClaims["sub"] ?: return null

        val legacyUserId = unverifiedIdTokenClaims["legacy_user_id"] as? String

        return IdTokenClaims(
            iss = unverifiedIdTokenClaims["iss"] as String,
            sub = sub as String,
            userId = legacyUserId ?: "",
            aud = emptyList(),
            exp = unverifiedIdTokenClaims["exp"] as Long,
            nonce = unverifiedIdTokenClaims["nonce"] as? String,
            amr = null
        )
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
