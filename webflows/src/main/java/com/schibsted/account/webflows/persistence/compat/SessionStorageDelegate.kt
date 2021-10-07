package com.schibsted.account.webflows.persistence.compat


import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import java.lang.reflect.Type
import java.security.InvalidKeyException
import javax.crypto.SecretKey
import kotlin.reflect.KProperty

internal class SessionStorageDelegate(
    context: Context,
    filename: String,
    private val encryptionKeyProvider: EncryptionKeyProvider = EncryptionKeyProvider.create(context),
    private val encryptionUtils: EncryptionUtils = EncryptionUtils.INSTANCE
) {

    companion object {
        private const val LOG_TAG = "SessionStorageDelegate"
        private const val PREFIX = "com.schibsted.account.persistence.SessionStorageDelegate"
        private const val PREF_MIGRATED = "$PREFIX.migrationCompleted"
        private const val PREF_KEY_DATA = "$PREFIX.sessions"
        private const val PREF_KEY_AES = "$PREFIX.aeskey"
        private val GSON = Gson()
    }

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(filename, Context.MODE_PRIVATE)
    }

    private lateinit var sessions: List<LegacySession>

    operator fun getValue(thisRef: Any?, property: KProperty<*>): List<LegacySession> {
        migrateLegacyData()
        if (!::sessions.isInitialized) {
            sessions = retrieveSessions()
        }
        return sessions
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: List<LegacySession>) {
        clearLegacyData()
        sessions = value
        storeSessions(value)
    }

    private fun retrieveSessions() = runCatching {
        readStorage()
    }.onFailure {
        Timber.e(it, "Failed to read legacy session storage.")
    }.getOrDefault(emptyList())

    private fun storeSessions(list: List<LegacySession>) {
        runCatching {
            if (encryptionKeyProvider.isKeyCloseToExpiration()) {
                removeDataAndKey()
                encryptionKeyProvider.refreshKeyPair()
            }
            writeStorage(list)
        }.onFailure {
            Timber.e(it, "Failed to write storage.")
            repairUnwritableStorage(list, it)
        }
    }

    /**
     * Retrieves sessions from [SharedPreferences]. If reading or decryption fails for any reason,
     * removes existing data and key from [SharedPreferences].
     */
    private fun readStorage(): List<LegacySession> {
        val data = retrieveStoredData() ?: return emptyList()
        val json = String(data)
        val typeToken: Type = object : TypeToken<List<LegacySession>>() {}.type
        return GSON.fromJson(json, typeToken)
    }

    /**
     * Stores sessions to [SharedPreferences]. If storing or encryption fails for any reason,
     * removes existing data and key from [SharedPreferences].
     */
    private fun writeStorage(items: List<LegacySession>) {
        val (secretKey, encryptedKey) = retrieveStoredKey() ?: generateSecretKey()
        val json = GSON.toJson(items).toByteArray()
        val encryptedData = encryptionUtils.aesEncrypt(json, secretKey)
        storeDataAndKey(encryptedData to encryptedKey)
    }

    /**
     * Reads [SharedPreferences], decrypts data (if it exists) and returns the result.
     * If decryption fails, removes stored data.
     */
    private fun retrieveStoredData(): ByteArray? {
        val (encryptedData, encryptedKey) = getDataAndKey() ?: return null
        val secretKey = recreateSecretKey(encryptedKey)
        return encryptionUtils.aesDecrypt(encryptedData, secretKey)
    }

    /**
     * Returns [SecretKey] and its encrypted [ByteArray] representation from [SharedPreferences],
     * or null, if it doesn't exist.
     */
    private fun retrieveStoredKey(): Pair<SecretKey, ByteArray>? {
        val (_, key) = getDataAndKey() ?: return null
        return recreateSecretKey(key) to key
    }

    /**
     * Builds a [SecretKey] from its encrypted [ByteArray] representation.
     */
    private fun recreateSecretKey(bytes: ByteArray): SecretKey {
        val privateRsaKey = encryptionKeyProvider.keyPair.private
        val decryptedAesKey = encryptionUtils.rsaDecrypt(bytes, privateRsaKey)
        return encryptionUtils.recreateAesKey(decryptedAesKey)
    }

    /**
     * Generates new [SecretKey] and its encrypted [ByteArray] representation.
     */
    private fun generateSecretKey(): Pair<SecretKey, ByteArray> {
        val secretKey = encryptionUtils.generateAesKey()
        val publicRsaKey = encryptionKeyProvider.keyPair.public
        val encodedKey = encryptionUtils.rsaEncrypt(secretKey.encoded, publicRsaKey)
        return secretKey to encodedKey
    }

    /**
     * Returns encrypted data and related AES key from [SharedPreferences].
     */
    private fun getDataAndKey(): Pair<ByteArray, ByteArray>? = prefs.run {
        val data = getBytes(PREF_KEY_DATA) ?: return null
        val key = getBytes(PREF_KEY_AES) ?: return null
        return data to key
    }

    /**
     * Stores encrypted data and related AES key (in its encrypted [ByteArray] representation)
     * to [SharedPreferences].
     */
    private fun storeDataAndKey(dataAndKey: Pair<ByteArray, ByteArray>) = prefs.edit().run {
        putBytes(PREF_KEY_DATA, dataAndKey.first)
        putBytes(PREF_KEY_AES, dataAndKey.second)
        apply()
    }

    private fun removeDataAndKey() = prefs.edit().run {
        remove(PREF_KEY_DATA)
        remove(PREF_KEY_AES)
        apply()
    }

    private fun SharedPreferences.Editor.putBytes(key: String, value: ByteArray) =
        putString(key, String(value.encodeBase64()))

    private fun SharedPreferences.getBytes(key: String): ByteArray? =
        getString(key, null)?.toByteArray()?.decodeBase64()

    private fun repairUnwritableStorage(list: List<LegacySession>, throwable: Throwable) {
        removeDataAndKey()
        if (throwable is InvalidKeyException) {
            try {
                encryptionKeyProvider.refreshKeyPair()
                writeStorage(list)
            } catch (e: Exception) {
                removeDataAndKey()
                Timber.e(e, "Failed to write storage with new RSA keys")
            }
        }
    }

    /**
     * Old versions of the SDK contained a couple of bugs in encryption/decryption utils.
     * Sessions encrypted in the old way cannot be decrypted using fixed encryption utils.
     * Therefore, we had to move the old decryption code to SessionsStorageLegacy.
     * Sessions encrypted in the new way are stored in a new location in SharedPreferences.
     * This lets us migrate the existing session list and not cause unintended "sign-outs".
     */
    private val legacy: SessionStorageLegacy? by lazy {
        // SessionStorageLegacy uses SharedPrefs. Calling its methods will involve disk IO.
        // Having this extra check allows us to avoid needless IO:
        if (prefs.getBoolean(PREF_MIGRATED, false)) {
            null
        } else {
            SessionStorageLegacy(appContext, encryptionKeyProvider, encryptionUtils)
        }
    }

    private fun clearLegacyData() = legacy?.run {
        clear()
        prefs.edit().putBoolean(PREF_MIGRATED, true).apply()
    }

    private fun migrateLegacyData() = legacy?.run {
        val legacyData = retrieve()
        clearLegacyData()
        if (!legacyData.isNullOrEmpty()) {
            storeSessions(legacyData)
        }
    }
}


/**
 * Old versions of the SDK contained a couple of bugs in encryption/decryption utils.
 * Sessions encrypted in the old way cannot be decrypted using fixed encryption utils.
 * This class provides access to legacy storage used by those old versions.
 */
private class SessionStorageLegacy(
    context: Context,
    private val encryptionKeyProvider: EncryptionKeyProvider,
    private val encryptionUtils: EncryptionUtils
) {
    companion object {
        private const val FILENAME = "IDENTITY_PREFERENCES"
        private const val KEY_AES = "IDENTITY_AES_PREF_KEY"
        private const val KEY_DATA = "IDENTITY_SESSIONS"
        private val GSON = Gson()
    }

    /**
     * Removes session data and its encryption key from legacy storage.
     */
    fun clear() {
        if (isEmpty) return

        prefs.edit().run {
            remove(KEY_AES)
            remove(KEY_DATA)
            apply()
        }
    }

    /**
     * Retrieves session data from legacy storage.
     */
    fun retrieve(): List<LegacySession>? {
        if (isEmpty) return null

        return runCatching { fetchSessions() }
            .getOrNull()
            .also {
                if (it == null) {
                    // Storage is not empty, yet the retrieved value is null.
                    // Storage is not readable because of inconsistent state.
                    clear()
                }
            }
    }

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    private val isEmpty: Boolean
        get() = !prefs.contains(KEY_AES) && !prefs.contains(KEY_DATA)

    private fun fetchSessions(): List<LegacySession>? {
        val encryptedKey = prefs.getBytes(KEY_AES) ?: return null
        val privateRsaKey = encryptionKeyProvider.keyPair.private
        val decryptedKey = encryptionUtils.rsaDecrypt(encryptedKey, privateRsaKey)
        val secretKey = encryptionUtils.recreateAesKey(decryptedKey)

        val encryptedData = prefs.getBytes(KEY_DATA) ?: return null
        // Old SDK versions used to encrypt data using empty ByteArray:
        val iv = ByteArray(16)
        val decryptedData = encryptionUtils.aesDecrypt(encryptedData, secretKey, iv)
        // Old SDK versions used to Base64-encode data twice:
        val decodedData = decryptedData.decodeBase64()
        val json = String(decodedData)
        val typeToken: Type = object : TypeToken<List<LegacySession>>() {}.type
        return GSON.fromJson(json, typeToken)
    }

    private fun SharedPreferences.getBytes(key: String): ByteArray? =
        getString(key, null)?.toByteArray()?.decodeBase64()
}
