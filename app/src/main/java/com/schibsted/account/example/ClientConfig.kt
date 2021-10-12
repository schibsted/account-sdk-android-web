package com.schibsted.account.example

import com.schibsted.account.webflows.client.Environment

object ClientConfig {
    @JvmStatic
    val environment = Environment.PRE
    const val clientId = "602525f2b41fa31789a95aa8"
    const val loginRedirectUri = "com.sdk-example.pre.602525f2b41fa31789a95aa8:/login"
    const val manualLoginRedirectUri = "com.sdk-example.pre.602525f2b41fa31789a95aa8:/manual-login"
    const val webClientId = "599fd705ed21dc0d55011d2a"
    const val webClientRedirectUri = "https://pre.sdk-example.com/safepage"
}
