package com.schibsted.account.webflows.loginPrompt

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.fragment.app.FragmentManager
import kotlinx.parcelize.Parcelize


@Parcelize
internal data class LoginPromptConfig(
    val authIntent: Intent,
    val isCancelable: Boolean = true
) : Parcelable

internal class LoginPromptManager(private val loginPromptConfig: LoginPromptConfig) {
    private val fragmentTag = "schibsted_account_login_prompt"

    /**
     * Show login prompt.
     *
     * @param supportFragmentManager Calling entity's fragment manager.
     */
    fun showLoginPromptIfAbsent(supportFragmentManager: FragmentManager) {
        val loginPromptFragment =
            supportFragmentManager.findFragmentByTag(fragmentTag) as? LoginPromptFragment

        if (loginPromptFragment == null) {
            initializeLoginPrompt(loginPromptConfig).show(supportFragmentManager, fragmentTag)
        }
    }

    private fun initializeLoginPrompt(config: LoginPromptConfig): LoginPromptFragment =
        LoginPromptFragment().apply {
            arguments = Bundle().apply {
                putParcelable(LoginPromptFragment.ARG_CONFIG, config)
            }
        }
}