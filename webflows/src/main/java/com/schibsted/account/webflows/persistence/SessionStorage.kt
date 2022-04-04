package com.schibsted.account.webflows.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.schibsted.account.webflows.user.StoredUserSession
import timber.log.Timber
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore

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
    private val gson = GsonBuilder().setDateFormat("MM dd, yyyy HH:mm:ss").create()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(context)
    }

    /**
     * Gets the encrypted shared preferences.
     *
     * This method will try and build the encrypted shared preferences, and in case of an error,
     * it will delete the existing file from the filesystem and attempt to recreate it.
     *
     * A layer of thread safeness is needed to be sure the shared preferences is only accessed
     * by one invoker at a time, to reduce potential corruption.
     */
    @Synchronized
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return try {
            buildSharedPreferences(context)
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()

            Timber.e("Error occurred while trying to build shared preferences, trying to recover", e)

            deleteSharedPreferences(context)

            buildSharedPreferences(context)
        }
    }

    private fun buildSharedPreferences(context: Context): SharedPreferences {
        Timber.d("Building the encrypted shared preferences")

        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFERENCE_FILENAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Deletes the encrypted shared preferences.
     *
     * This method will try and find the actual shared preferences xml file and delete it. It
     * will also try to delete the master key entry in the keystore.
     */
    private fun deleteSharedPreferences(context: Context) {
        Timber.d("Trying to delete encrypted shared preferences")

        try {
            val sharedPreferencesFile = File(context.filesDir?.parent + "/shared_prefs/" + PREFERENCE_FILENAME + ".xml")

            Timber.d("Shared preferences location: ${sharedPreferencesFile.absolutePath}")

            if (sharedPreferencesFile.exists()) {
                val deleted = sharedPreferencesFile.delete()

                Timber.d("deleteSharedPreferences() Shared prefs file deleted: $deleted; path: ${sharedPreferencesFile.absolutePath}")
            } else {
                Timber.d("deleteSharedPreferences() Shared prefs file non-existent; path: ${sharedPreferencesFile.absolutePath}")
            }

            Timber.d("Deleting master key entry in keystore")

            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)

            Timber.d("Finished deleting encrypted shared preferences")
        } catch (e: Exception) {
            e.printStackTrace()

            Timber.e("Error occurred while trying to delete encrypted shared preferences", e)
        }
    }

    @Synchronized
    override fun save(session: StoredUserSession) {
        val editor = prefs.edit()
        val json = gson.toJson(session)
        editor.putString(session.clientId, json)
        editor.apply()
    }

    @Synchronized
    override fun get(clientId: String, callback: (StoredUserSession?) -> Unit) {
        val json = prefs.getString(clientId, null) ?: return callback(null)
        callback(gson.getStoredUserSession(json) ?: Gson().getStoredUserSession(json))
    }

    private fun Gson.getStoredUserSession(json: String): StoredUserSession? {
        return try {
            fromJson(json, StoredUserSession::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    @Synchronized
    override fun remove(clientId: String) {
        val editor = prefs.edit()
        editor.remove(clientId)
        editor.apply()
    }

    companion object {
        const val PREFERENCE_FILENAME = "SCHACC_TOKENS"
    }
}
