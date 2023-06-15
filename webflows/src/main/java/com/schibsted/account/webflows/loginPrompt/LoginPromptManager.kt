package com.schibsted.account.webflows.loginPrompt

import androidx.fragment.app.FragmentManager
import com.schibsted.account.webflows.client.Client

class LoginPromptConfig {
    var client: Client
    var isCancelable: Boolean

    constructor(client: Client, isCancelable: Boolean = true) {
        this.client = client
        this.isCancelable = isCancelable
    }
}

class LoginPromptManager {
    val loginPromptConfig: LoginPromptConfig

    constructor(loginPromptConfig: LoginPromptConfig) {
        this.loginPromptConfig = loginPromptConfig
    }

    /**
     * Show login prompt.
     *
     * @param supportFragmentManager Calling entity's fragment manager.
     */
    fun showLoginPrompt(
        loginPromptFragment: LoginPromptFragment,
        supportFragmentManager: FragmentManager
    ) {
        loginPromptFragment?.show(supportFragmentManager, null)
    }

    fun initializeLoginPrompt(): LoginPromptFragment {
        var loginPromptFragment = LoginPromptFragment()
        loginPromptFragment.loginPromptConfig = this.loginPromptConfig

        return loginPromptFragment
    }
}