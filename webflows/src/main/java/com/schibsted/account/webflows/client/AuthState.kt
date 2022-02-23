package com.schibsted.account.webflows.client

/**
 * OAuth data to be persisted for an ongoing authentication flow.
 *
 * After the authentication has completed, and the user has been redirected back to the app, this
 * data should be used to verify the authentication response and to make the token request.
 */
internal data class AuthState(
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val mfa: MfaType?
)

enum class MfaType(val value: String) {
    PASSWORD("password"),
    OTP("otp"),
    SMS("sms"),
    EID_NO("eid-no"), //Only used for PRE environment
    EID_SE("eid-se"), //Only used for PRE environment
    EID("eid"), // For Production
}
