package com.schibsted.account.android.webflows.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.schibsted.account.android.webflows.user.UserSession

internal interface SessionStorage {
    fun save(session: UserSession)
    fun get(clientId: String): UserSession?
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

    override fun save(session: UserSession) {
        val editor = prefs.edit()
        val json = gson.toJson(session)
        editor.putString(session.clientId, json)
        editor.apply()
    }

    override fun get(clientId: String): UserSession? {
        val json = prefs.getString(clientId, null) ?: return null
        return gson.fromJson(json, UserSession::class.java)
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
