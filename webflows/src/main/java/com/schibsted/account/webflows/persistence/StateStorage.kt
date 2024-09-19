package com.schibsted.account.webflows.persistence

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import kotlin.reflect.KClass

/**
 * Auxiliary storage to keep state during the login flow.
 *
 * Uses Shared Preferences as backing storage to persist the data.
 */
internal class StateStorage(context: Context) {
    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
    }

    fun <T> setValue(
        key: String,
        value: T,
    ) {
        val editor = prefs.edit()
        val json = gson.toJson(value)
        editor.putString(key, json)
        editor.apply()
    }

    fun <T : Any> getValue(
        key: String,
        c: KClass<T>,
    ): T? {
        val json = prefs.getString(key, null) ?: return null
        return gson.fromJson(json, c.java)
    }

    fun removeValue(key: String) {
        val editor = prefs.edit()
        editor.remove(key)
        editor.apply()
    }

    companion object {
        const val PREFERENCE_FILENAME = "SCHACC"
    }
}
