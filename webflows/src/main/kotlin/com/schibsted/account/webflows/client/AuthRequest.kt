package com.schibsted.account.webflows.client

/**
 * Authentication request parameters. For more information about possible values, see
 *  <a href="https://docs.schibsted.io/schibsted-account/guides/authentication/#required-parameters">here</a>
 *
 * @property extraScopeValues Extra scope values to request. By default `openid` and
 *  `offline_access` will always be included.
 * @property mfa Optional MFA verification to prompt the user with.
 * @property loginHint User identifier to be prefilled in the login flow.
 */
data class AuthRequest @JvmOverloads constructor(
    val extraScopeValues: Set<String> = setOf(),
    val mfa: MfaType? = null,
    val loginHint: String? = null
)
