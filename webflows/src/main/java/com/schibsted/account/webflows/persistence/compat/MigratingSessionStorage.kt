package com.schibsted.account.webflows.persistence.compat

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.persistence.EncryptedSharedPrefsStorage
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Logging.SDK_TAG

internal class MigratingSessionStorage(
    private val client: Client,
    private val newStorage: SessionStorage,
    private val legacyStorage: LegacySessionStorage,
    private val legacyClientId: String,
    private val legacyClientSecret: String
) : SessionStorage {

    internal constructor(
        context: Context,
        client: Client,
        legacyClientId: String,
        legacyClientSecret: String
    ) : this(
        client,
        EncryptedSharedPrefsStorage(context),
        LegacySessionStorage(context),
        legacyClientId,
        legacyClientSecret
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
                    val legacyClient =
                        LegacyClient(legacyClientId, legacyClientSecret, client.schibstedAccountApi)
                    migrateSession(legacySession, legacyClient, callback)
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

    @VisibleForTesting
    internal fun migrateSession(
        legacySession: StoredUserSession,
        legacyClient: LegacyClient,
        callback: (StoredUserSession?) -> Unit
    ) {
        // use tokens from legacy session to get OAuth auth code for the new client
        legacyClient.getAuthCodeFromTokens(
            legacySession.userTokens,
            client.configuration.clientId
        ) { codeResult ->
            codeResult
                .map { codeResponse ->
                    client.makeTokenRequest(codeResponse.code, null) {
                        it
                            .foreach { migratedSession ->
                                newStorage.save(migratedSession)
                                legacyStorage.remove()
                                callback(migratedSession)
                            }
                            .left().foreach {
                                Log.e(SDK_TAG, "Failed to migrate tokens: $it")
                                callback(null)
                            }
                    }
                }
                .left().map {
                    Log.e(SDK_TAG, "Failed to migrate tokens: $it")
                    callback(null)
                }
        }
    }
}
