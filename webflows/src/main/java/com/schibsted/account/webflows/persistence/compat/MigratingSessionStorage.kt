package com.schibsted.account.webflows.persistence.compat

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.persistence.EncryptedSharedPrefsStorage
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.persistence.StorageReadCallback
import com.schibsted.account.webflows.user.MigrationStoredUserSession
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either
import timber.log.Timber

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
        legacyClientSecret: String,
        legacySharedPrefsFilename: String?
    ) : this(
        client,
        EncryptedSharedPrefsStorage(context),
        LegacySessionStorage(context, legacySharedPrefsFilename),
        legacyClientId,
        legacyClientSecret
    )

    override fun save(session: StoredUserSession) {
        // only delegate to new storage; no need to store in legacy storage
        newStorage.save(session)
    }

    override fun get(clientId: String, callback: StorageReadCallback) {
        // try new storage first
        newStorage.get(clientId) { result ->
            result
                .onSuccess { newSession ->
                    if (newSession != null) {
                        callback(Either.Right(newSession))
                    } else {
                        // if no existing session found, look in legacy storage
                        lookupLegacyStorage(callback)
                    }
                }
                .onFailure {
                    lookupLegacyStorage(callback)
                }
        }
    }

    override fun remove(clientId: String) {
        // only delegate to new storage; data should have already been removed from legacy storage during migration
        newStorage.remove(clientId)
    }

    @VisibleForTesting
    internal fun migrateSession(
        legacySession: MigrationStoredUserSession,
        legacyClient: LegacyClient,
        callback: StorageReadCallback
    ) {
        // use tokens from legacy session to get OAuth auth code for the new client
        legacyClient.getAuthCodeFromTokens(
            legacySession.userTokens,
            client.configuration.clientId
        ) { codeResult ->
            codeResult
                .onSuccess { codeResponse ->
                    client.makeTokenRequest(codeResponse.code, null) { storedUserSession ->
                        storedUserSession
                            .onSuccess { migratedSession ->
                                newStorage.save(migratedSession)
                                legacyStorage.remove()
                                callback(Either.Right(migratedSession))
                            }
                            .onFailure { tokenError ->
                                Timber.e("Failed to migrate tokens: $tokenError")
                                callback(Either.Right(null))
                            }
                    }
                }
                .onFailure { httpError ->
                    Timber.e("Failed to migrate tokens: $httpError")
                    callback(Either.Right(null))
                }
        }
    }

    private fun lookupLegacyStorage(callback: StorageReadCallback) {
        val legacySession = legacyStorage.get(legacyClientId)
        if (legacySession != null) {
            val legacyClient =
                LegacyClient(
                    legacyClientId,
                    legacyClientSecret,
                    client.schibstedAccountApi
                )
            migrateSession(legacySession, legacyClient, callback)
        } else {
            callback(Either.Right(null))
        }
    }
}
