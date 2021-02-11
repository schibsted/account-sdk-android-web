package com.schibsted.account.android.webflows.persistance

import android.content.Context
import com.google.gson.Gson
import com.schibsted.account.android.webflows.client.MfaType

class WebViewDataStorage(context: Context) : AbstractStorage(context) {
    private val appContext: Context = context.applicationContext

    fun store(data: WebViewData) {
        val editor = prefs.edit()
        val gson = Gson() // Todo check if it should be initialized higher, heavy object
        val json = gson.toJson(data)
        editor.putString("WebViewData", json)
        editor.apply()
    }

    fun retrieve(): WebViewData? {
        val gson = Gson()
        val json = prefs.getString("WebViewData", null)
        return gson.fromJson(json, WebViewData::class.java);
    }

}

data class WebViewData(
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val mfa: MfaType?
)