package com.schibsted.account.webflows.client.listener

import java.security.GeneralSecurityException

/**
 * An event listener for the creation of the encrypted preferences.
 * One of the teams has a strange issue with user being logged out
 * and they suspect the androidx.crypto library might be the reason.
 */
abstract class WebFlowsEventListener {

    open fun preferencesReadStart() {}

    open fun preferencesReadEnd() {}

    open fun createEncryptedSharedPreferences() {}

    open fun createEncryptedSharedPreferencesFailure(e: GeneralSecurityException) {}

    open fun preferencesReadError(e: SecurityException) {}

    open fun deletePreferencesStart() {}

    open fun deletePreferencesEnd() {}

    open fun deletePreferencesError() {}


    companion object {
        val NONE = object : WebFlowsEventListener() {}
    }
}