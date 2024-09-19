package com.schibsted.account.example

import com.schibsted.account.webflows.client.Environment

object ClientConfig {
    @JvmStatic
    val environment = Environment.PRE
    const val CLIENT_ID = "602525f2b41fa31789a95aa8"
    const val LOGIN_REDIRECT_URI = "com.sdk-example.pre.602525f2b41fa31789a95aa8:///login"
    const val MANUAL_LOGIN_REDIRECT_URI = "com.sdk-example.pre.602525f2b41fa31789a95aa8:///manual-login"
    const val WEB_CLIENT_ID = "599fd705ed21dc0d55011d2a"
    const val WEB_CLIENT_REDIRECT_URI = "https://pre.sdk-example.com/safepage"
}
