package com.schibsted.account.webflows.persistence

import android.content.Context
import android.content.ContentResolver
import android.content.ContentValues
import android.content.SharedPreferences
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.schibsted.account.webflows.loginPrompt.LoginPromptContentProvider
import com.schibsted.account.webflows.loginPrompt.SessionInfoManager
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either
import timber.log.Timber
import java.security.GeneralSecurityException

internal typealias StorageReadResult = Either<StorageError, StoredUserSession?>
internal typealias StorageReadCallback = (StorageReadResult) -> Unit

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

internal class MigratingSessionStorage(
    private val newStorage: SharedPrefsStorage,
    private val previousStorage: EncryptedSharedPrefsStorage,
) : SessionStorage {

    override fun save(session: StoredUserSession) {
        newStorage.save(session)
    }

    override fun get(clientId: String, callback: StorageReadCallback) {
        newStorage.get(clientId) { result ->
            result
                .onSuccess { newSession ->
                    if (newSession != null) {
                        callback(Either.Right(newSession))
                    } else {
                        // if no existing session found, look in previous storage
                        lookupPreviousStorage(clientId, callback)
                    }
                }
                .onFailure { lookupPreviousStorage(clientId, callback) }
        }
    }

    private fun lookupPreviousStorage(clientId: String, callback: StorageReadCallback) {
        previousStorage.get(clientId) { result ->
            result.onSuccess {
                it?.let {
                    // migrate existing session
                    newStorage.save(it)
                    previousStorage.remove(clientId)
                }
            }
            callback(result)
        }
    }

    override fun remove(clientId: String) {
        newStorage.remove(clientId)
    }
}

internal class EncryptedSharedPrefsStorage(context: Context) : SessionStorage {
    private val gson = GsonBuilder().setDateFormat("MM dd, yyyy HH:mm:ss").create()
    private val sessionInfoManager = SessionInfoManager(context)


    private val prefs: SharedPreferences? by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        try {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFERENCE_FILENAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            Timber.e(
                "Error occurred while trying to build encrypted shared preferences",
                e
            )
            null
        }
    }

    override fun save(session: StoredUserSession) {
        try {
            val editor = prefs?.edit()
            val json = gson.toJson(session)
            editor?.putString(session.clientId, json)
            editor?.apply()
            sessionInfoManager.save()
        } catch (e: SecurityException) {
            Timber.e(
                "Error occurred while trying to write to encrypted shared preferences",
                e
            )
            throw e
        }
    }

    override fun get(clientId: String, callback: StorageReadCallback) {
        try {
            val json = prefs?.getString(clientId, null) ?: return callback(Either.Right(null))
            callback(gson.getStoredUserSession(json))
        } catch (e: SecurityException) {
            Timber.e(
                "Error occurred while trying to read from encrypted shared preferences",
                e
            )
            callback(Either.Left(StorageError.UnexpectedError(e)))
        }
    }

    override fun remove(clientId: String) {
        try {
            val editor = prefs?.edit()
            editor?.remove(clientId)
            editor?.apply()
            sessionInfoManager.clear()
        } catch (e: SecurityException) {
            Timber.e(
                "Error occurred while trying to delete from encrypted shared preferences",
                e
            )
        }
    }

    companion object {
        private const val PREFERENCE_FILENAME = "SCHACC_TOKENS"
    }
}

internal class SharedPrefsStorage(context: Context) : SessionStorage {

    private val gson = GsonBuilder().setDateFormat("MM dd, yyyy HH:mm:ss").create()
    private val prefs = context.getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)

    override fun save(session: StoredUserSession) {
        val editor = prefs.edit()
        editor.putString(session.clientId, gson.toJson(session))
        editor.apply()
    }

    override fun get(clientId: String, callback: StorageReadCallback) {
        val json = prefs.getString(clientId, null)
        callback(gson.getStoredUserSession(json))
    }

    override fun remove(clientId: String) {
        val editor = prefs.edit()
        editor.remove(clientId)
        editor.apply()
    }

    companion object {
        private const val PREFERENCE_FILENAME = "SCHACC_TOKENS_SHARED_PREFS"
    }
}

private fun Gson.getStoredUserSession(json: String?): StorageReadResult {
    return try {
        Either.Right(fromJson(json, StoredUserSession::class.java))
    } catch (e: JsonSyntaxException) {
        Either.Left(StorageError.UnexpectedError(e))
    }
}
