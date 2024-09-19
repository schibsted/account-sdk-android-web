package com.schibsted.account.webflows.client

import java.net.URL

data class ClientConfiguration(val serverUrl: URL, val clientId: String, val redirectUri: String) {
    val issuer: String = serverUrl.toString()

    constructor(env: Environment, clientId: String, redirectUri: String) : this(
        env.url,
        clientId,
        redirectUri,
    )
}
