package com.schibsted.account.example

import android.util.Log
import com.schibsted.account.webflows.client.listener.WebFlowsEventListener
import java.security.GeneralSecurityException

class LoggingEventListener : WebFlowsEventListener() {
    override fun preferencesReadStart() {
        Log.d("LoggingEventListener", "preferencesReadStart")
    }

    override fun preferencesReadEnd() {
        Log.d("LoggingEventListener", "preferencesReadEnd")

    }

    override fun createEncryptedSharedPreferences() {
        Log.d("LoggingEventListener", "createEncryptedSharedPreferences")

    }

    override fun createEncryptedSharedPreferencesFailure(e: GeneralSecurityException) {
        Log.d("LoggingEventListener", "createEncryptedSharedPreferencesFailure")

    }

    override fun preferencesReadError(e: SecurityException) {
        Log.d("LoggingEventListener", "preferencesReadError ${e.message}")

    }

    override fun deletePreferencesStart() {
        Log.d("LoggingEventListener", "deletePreferencesStart")

    }

    override fun deletePreferencesEnd() {
        Log.d("LoggingEventListener", "deletePreferencesEnd")

    }

    override fun deletePreferencesError() {
        Log.d("LoggingEventListener", "deletePreferencesError")

    }
}