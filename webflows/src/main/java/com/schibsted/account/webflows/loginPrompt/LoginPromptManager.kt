package com.schibsted.account.webflows.loginPrompt

import androidx.fragment.app.FragmentManager
import com.schibsted.account.webflows.client.Client

internal class LoginPromptConfig {
    var client: Client
    var isCancelable: Boolean

    constructor(client: Client, isCancelable: Boolean = true) {
        this.client = client
        this.isCancelable = isCancelable
    }
}

internal class LoginPromptManager {
    val loginPromptConfig: LoginPromptConfig
    val fragmentTag = "schibsted_account_login_prompt"

    constructor(loginPromptConfig: LoginPromptConfig) {
        this.loginPromptConfig = loginPromptConfig
    }

    /**
     * Show login prompt.
     *
     * @param supportFragmentManager Calling entity's fragment manager.
     */
    fun showLoginPrompt(supportFragmentManager: FragmentManager) {
        var loginPromptFragment =
            supportFragmentManager.findFragmentByTag(fragmentTag) as? LoginPromptFragment
                ?: initializeLoginPrompt()
        loginPromptFragment.loginPromptConfig = this.loginPromptConfig
        loginPromptFragment.show(supportFragmentManager, fragmentTag)
    }

    internal fun initializeLoginPrompt(): LoginPromptFragment {
        var loginPromptFragment = LoginPromptFragment()
        return loginPromptFragment
    }
}