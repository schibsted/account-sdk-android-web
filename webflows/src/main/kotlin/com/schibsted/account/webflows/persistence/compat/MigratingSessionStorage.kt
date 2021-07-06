package com.schibsted.account.webflows.persistence.compat

import android.content.Context
import android.util.Log
import com.schibsted.account.webflows.client.AuthState
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.persistence.EncryptedSharedPrefsStorage
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Logging.SDK_TAG

internal class MigratingSessionStorage(
    private val client: Client,
    private val newStorage: SessionStorage,
    private val legacyStorage: LegacySessionStorage,
    private val legacyClientId: String
) : SessionStorage {

    internal constructor(context: Context, client: Client, legacyClientId: String) : this(
        client,
        EncryptedSharedPrefsStorage(context),
        LegacySessionStorage(context),
        legacyClientId
    )

    override fun save(session: StoredUserSession) {
        // only delegate to new storage; no need to store in legacy storage
        newStorage.save(session)
    }

    override fun get(clientId: String, callback: (StoredUserSession?) -> Unit) {
        // try new storage first
        newStorage.get(clientId) { newSession ->
            if (newSession != null) {
                callback(newSession)
            } else {
                // if no existing session found, look in legacy storage
                val legacySession = legacyStorage.get(legacyClientId)
                if (legacySession != null) {
                    migrateSession(legacySession, callback)
                } else {
                    callback(null)
                }
            }
        }
    }

    override fun remove(clientId: String) {
        // only delegate to new storage; data should have already been removed from legacy storage during migration
        newStorage.remove(clientId)
    }

    private fun migrateSession(
        legacySession: StoredUserSession,
        callback: (StoredUserSession?) -> Unit
    ) {
        // use tokens from legacy session to get OAuth auth code for the new client
        val legacyClientConfig =
            client.configuration.copy(clientId = legacyClientId, redirectUri = "")
        val legacyClient = client.copy(legacyClientConfig)
        val legacyUser = User(legacyClient, legacySession.userTokens)
        // TODO test
        // 1. expired access token, but valid refresh token
        // 2. both invalid access token and refresh token

        legacyUser.oneTimeCode(client.configuration.clientId) { codeResult ->
            codeResult
                .map { code ->
                    client.makeTokenRequest(code, AuthState("", null, null, null)) {
                        it
                            .foreach { migratedSession ->
                                newStorage.save(migratedSession)
                                legacyStorage.remove()
                                callback(migratedSession)
                            }
                            .left().foreach {
                                Log.e(SDK_TAG, "Failed to migrate tokens: $it")
                                callback(null) // TODO test?
                            }
                    }
                }
                .left().map {
                    Log.e(SDK_TAG, "Failed to migrate tokens: $it")
                    callback(null) // TODO test?
                }
        }
    }
}
