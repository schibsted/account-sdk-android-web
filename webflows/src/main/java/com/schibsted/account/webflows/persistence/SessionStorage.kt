package com.schibsted.account.webflows.persistence

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either
import timber.log.Timber
import java.security.GeneralSecurityException
import java.security.KeyStore

internal typealias StorageReadCallback = (Either<StorageError, StoredUserSession?>) -> Unit

/**
 * User session storage.
 *
 * A user session holds all the user tokens (access, refresh and id token) issued to the client.
 * They must be stored securely, e.g. in encrypted.
 */
internal interface SessionStorage {
    fun save(session: StoredUserSession)
    fun get(clientId: String, callback: StorageReadCallback)
    fun remove(clientId: String)
}

internal class EncryptedSharedPrefsStorage(context: Context) : SessionStorage {
    private val gson = GsonBuilder().setDateFormat("MM dd, yyyy HH:mm:ss").create()
    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        createEncryptedSharedPrefDestructively(context.applicationContext)
    }

    override fun save(session: StoredUserSession) {
        try {
            val editor = prefs.edit()
            val json = gson.toJson(session)
            editor.putString(session.clientId, json)
            editor.apply()
        } catch (e: SecurityException) {
            Timber.e(
                "Error occurred while trying to write to encrypted shared preferences",
                e
            )
        }
    }

    override fun get(clientId: String, callback: StorageReadCallback) {
        try {
            val json = prefs.getString(clientId, null) ?: return callback(Either.Right(null))
            callback(
                Either.Right(
                    gson.getStoredUserSession(json) ?: Gson().getStoredUserSession(
                        json
                    )
                )
            )
        } catch (e: SecurityException) {
            Timber.e(
                "Error occurred while trying to read from encrypted shared preferences",
                e
            )

            createEncryptedSharedPrefDestructively(this.appContext)
        }
    }

    override fun remove(clientId: String) {
        try {
            val editor = prefs.edit()
            editor.remove(clientId)
            editor.apply()
        } catch (e: SecurityException) {
            Timber.e(
                "Error occurred while trying to delete from encrypted shared preferences",
                e
            )
        }
    }

    private fun createEncryptedSharedPrefDestructively(context: Context): SharedPreferences {
        try {
            return createEncryptedSharedPref(context)
        } catch (e: GeneralSecurityException) {
            Timber.e(
                "Error occurred while trying to build encrypted shared preferences. Cleaning corrupted data",
                e
            )
            deleteMasterKeyEntry()
            deleteExistingPref(context)
        }

        try {
            return createEncryptedSharedPref(context)
        } catch (e: GeneralSecurityException) {
            Timber.e(
                "Encrypted shared preferences could not be created",
                e
            )

            throw e
        }
    }

    private fun deleteExistingPref(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.deleteSharedPreferences(PREFERENCE_FILENAME)
        } else {
            context.getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    private fun deleteMasterKeyEntry() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    private fun createEncryptedSharedPref(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFERENCE_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun Gson.getStoredUserSession(json: String): StoredUserSession? {
        return try {
            fromJson(json, StoredUserSession::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    companion object {
        const val PREFERENCE_FILENAME = "SCHACC_TOKENS"
    }
}
