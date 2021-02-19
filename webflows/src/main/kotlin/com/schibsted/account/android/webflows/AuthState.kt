package com.schibsted.account.android.webflows

internal data class AuthState(
    val state: String,
    val nonce: String,
    val codeVerifier: String,
    val mfa: MfaType?
)

enum class MfaType(val value: String) {
    PASSWORD("password"),
    OTP("otp"),
    SMS("sms")
}
