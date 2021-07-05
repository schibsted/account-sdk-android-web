package com.schibsted.account.webflows.persistence.compat

import android.content.Context
import com.schibsted.account.webflows.persistence.EncryptedSharedPrefsStorage
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.user.StoredUserSession

internal class MigratingSessionStorage(context: Context, private val legacyClientId: String): SessionStorage {
    private val newStorage: SessionStorage = EncryptedSharedPrefsStorage(context)
    private val legacyStorage = LegacySessionStorage(context)

    override fun save(session: StoredUserSession) {
        // only delegate to new storage; no need to store in legacy storage
        newStorage.save(session)
    }

    override fun get(clientId: String): StoredUserSession? {
        // try new storage first
        val newSession = newStorage.get(clientId)
        if (newSession != null) {
            return newSession
        }

        // if no existing session found, look in legacy storage
        val legacySession = legacyStorage.get(legacyClientId) ?: return null

        // TODO exchange tokens from legacy session for new tokens for clientId
        val migratedSession = legacySession.copy(clientId=clientId)

        newStorage.save(migratedSession)
        legacyStorage.remove()

        return legacySession
    }

    override fun remove(clientId: String) {
        // only delegate to new storage; data should have already been removed from legacy storage during migration
        newStorage.remove(clientId)
    }
}
