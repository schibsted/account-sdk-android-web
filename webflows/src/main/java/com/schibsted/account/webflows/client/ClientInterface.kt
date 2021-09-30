package com.schibsted.account.webflows.client

import android.content.Context
import android.content.Intent
import com.schibsted.account.webflows.activities.AuthorizationManagementActivity
import com.schibsted.account.webflows.user.User

interface ClientInterface {
    /**
     * Start login flow.
     *
     * Requires [AuthorizationManagementActivity.setup] to have been called before this.
     *
     * @param authRequest Authentication request parameters.
     */
    fun getAuthenticationIntent(context: Context, authRequest: AuthRequest = AuthRequest()): Intent

    /**
     * Start auth activity manually.
     *
     * @param authRequest Authentication request parameters.
     */
    fun launchAuth(context: Context, authRequest: AuthRequest = AuthRequest())

    /**
     * Call this with the intent received via deep link to complete the login flow.
     *
     * This only needs to be used if manually starting the login flow using [launchAuth].
     * If using [getAuthenticationIntent] this step will be handled for you.
     */
    fun handleAuthenticationResponse(intent: Intent, callback: LoginResultHandler)

    /** Resume any previously logged-in user session */
    fun resumeLastLoggedInUser(): User?
}