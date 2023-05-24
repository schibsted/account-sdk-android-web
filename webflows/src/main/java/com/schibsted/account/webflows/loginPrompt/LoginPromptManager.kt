package com.schibsted.account.webflows.loginPrompt

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.schibsted.account.webflows.activities.AuthorizationManagementActivity
import com.schibsted.account.webflows.client.Client

class LoginPromptConfig {
    var client: Client
    var context: Context
    var isCancelable : Boolean

    constructor(client: Client, context: Context, isCancelable: Boolean = true ) {
        this.client=client
        this.context=context
        this.isCancelable=isCancelable
    }
}

class LoginPromptManager {
    internal val loginPromptFragment: LoginPromptFragment

    constructor(loginPromptConfig: LoginPromptConfig) {
        this.loginPromptFragment = LoginPromptFragment()
        this.loginPromptFragment.loginPromptConfig = loginPromptConfig
    }

    /**
     * Show login prompt.
     *
     * @param supportFragmentManager Calling entity's fragment manager.
     */
    fun showLoginPrompt(supportFragmentManager: FragmentManager) {
        loginPromptFragment?.show(supportFragmentManager, null)
    }

    /**
     * Allows updating login prompt configuration without requiring to recreate manager class.
     *
     * @param loginPromptConfig Configuration object for login prompt.
     */
    fun updateLoginPromptConfig(loginPromptConfig: LoginPromptConfig) {
        this.loginPromptFragment.loginPromptConfig = loginPromptConfig
    }
}