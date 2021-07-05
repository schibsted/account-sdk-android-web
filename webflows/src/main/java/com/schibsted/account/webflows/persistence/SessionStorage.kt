package com.schibsted.account.webflows.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.schibsted.account.webflows.user.StoredUserSession

/**
 * User session storage.
 *
 * A user session holds all the user tokens (access, refresh and id token) issued to the client.
 * They must be stored securely, e.g. in encrypted.
 */
internal interface SessionStorage {
    fun save(session: StoredUserSession)
    fun get(clientId: String, callback: (StoredUserSession?) -> Unit)
    fun remove(clientId: String)
}

internal class EncryptedSharedPrefsStorage(context: Context) : SessionStorage {
    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFERENCE_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun save(session: StoredUserSession) {
        val editor = prefs.edit()
        val json = gson.toJson(session)
        editor.putString(session.clientId, json)
        editor.apply()
    }

    override fun get(clientId: String, callback: (StoredUserSession?) -> Unit){
        val json = prefs.getString(clientId, null) ?: return callback(null)
        callback(gson.fromJson(json, StoredUserSession::class.java))
    }

    override fun remove(clientId: String) {
        val editor = prefs.edit()
        editor.remove(clientId)
        editor.apply()
    }

    companion object {
        const val PREFERENCE_FILENAME = "SCHACC_TOKENS"
    }
}
