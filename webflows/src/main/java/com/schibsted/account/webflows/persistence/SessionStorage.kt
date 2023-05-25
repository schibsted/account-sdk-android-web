package com.schibsted.account.webflows.persistence

import android.content.Context
import android.content.ContentResolver
import android.content.ContentValues
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.schibsted.account.webflows.loginPrompt.LoginPromptContentProvider
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either
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

internal class EncryptedSharedPrefsStorage(context: Context) : SessionStorage {
    private val gson = GsonBuilder().setDateFormat("MM dd, yyyy HH:mm:ss").create()
    private val contentResolver = context.contentResolver;
    private val packageName = context.packageName

    private val prefs: SharedPreferences by lazy {
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
            throw e
        }
    }

    override fun save(session: StoredUserSession) {
        try {
            val editor = prefs.edit()
            val json = gson.toJson(session)
            editor.putString(session.clientId, json)
            editor.apply()
            contentResolver.insert(LoginPromptContentProvider.CONTENT_URI, ContentValues().apply {
              put("packageName", packageName)
            })
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
            callback(Either.Left(StorageError.UnexpectedError(e)))
        }
    }

    private fun Gson.getStoredUserSession(json: String): StoredUserSession? {
        return try {
            fromJson(json, StoredUserSession::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    override fun remove(clientId: String) {
        try {
            val editor = prefs.edit()
            editor.remove(clientId)
            editor.apply()
            contentResolver.delete(LoginPromptContentProvider.CONTENT_URI, null, arrayOf(packageName))
        } catch (e: SecurityException) {
            Timber.e(
                "Error occurred while trying to delete from encrypted shared preferences",
                e
            )
        }
    }

    companion object {
        const val PREFERENCE_FILENAME = "SCHACC_TOKENS"
    }
}
