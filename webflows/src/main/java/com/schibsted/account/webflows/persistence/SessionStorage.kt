package com.schibsted.account.webflows.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.GsonBuilder
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Util.getStoredUserSession
import timber.log.Timber
import java.security.GeneralSecurityException

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

internal class MigratingSessionStorage(
    private val newStorage: SharedPrefsStorage,
    private val previousStorage: EncryptedSharedPrefsStorage,
) : SessionStorage {

    override fun save(session: StoredUserSession) {
        newStorage.save(session)
    }

    override fun get(clientId: String, callback: StorageReadCallback) {
        try {
            when (newStorage.getJsonForClientId(clientId)) {
                null -> {
                    previousStorage.get(clientId, object : StorageReadCallback {
                        override fun invoke(result: Either<StorageError, StoredUserSession?>) {
                            if (result is Either.Right) {
                                result.value?.let {
                                    save(result.value)
                                    callback(Either.Right(result.value))
                                }
                            }
                        }
                    })
                }
                else -> {
                    newStorage.get(clientId, callback)
                }
            }
        } catch (e: ClassCastException) {
            Timber.e(e)
            callback(Either.Left(StorageError.UnexpectedError(e)))
        }
    }

    override fun remove(clientId: String) {
        newStorage.remove(clientId)
    }
}

internal class EncryptedSharedPrefsStorage(context: Context) : SessionStorage {
    private val gson = GsonBuilder().setDateFormat("MM dd, yyyy HH:mm:ss").create()

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
        } catch (e: SecurityException) {
            Timber.e(
                "Error occurred while trying to write to encrypted shared preferences",
                e
            )
        }
    }

    override fun get(clientId: String, callback: StorageReadCallback) {
        try {
            val json = prefs?.getString(clientId, null) ?: return callback(Either.Right(null))
            callback(Either.Right(gson.getStoredUserSession(json)))
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
        callback(Either.Right(value = gson.getStoredUserSession(json)))
    }

    override fun remove(clientId: String) {
        val editor = prefs.edit()
        editor.remove(clientId)
        editor.apply()
    }

    fun getJsonForClientId(clientId: String): String? = prefs.getString(clientId, null)

    companion object {
        private const val PREFERENCE_FILENAME = "SCHACC_TOKENS_SHARED_PREFS"
    }
}