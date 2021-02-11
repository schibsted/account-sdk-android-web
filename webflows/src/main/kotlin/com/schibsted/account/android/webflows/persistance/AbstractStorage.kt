package com.schibsted.account.android.webflows.persistance

import android.content.Context
import android.content.SharedPreferences

abstract class AbstractStorage(private val context: Context) {

    protected val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFERENCE_FILENAME, Context.MODE_PRIVATE)
    }

    companion object {
        const val PREFERENCE_FILENAME = "SCHACC"
    }

}