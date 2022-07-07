package com.schibsted.account.webflows.client.listener

import java.security.GeneralSecurityException

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