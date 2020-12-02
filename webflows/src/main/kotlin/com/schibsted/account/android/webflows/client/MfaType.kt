package com.schibsted.account.android.webflows.client

enum class MfaType(val value: String) {
    PASSWORD("password"),
    OTP("otp"),
    SMS("sms")
}