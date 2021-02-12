package com.schibsted.account.android.webflows.client

import java.net.URL

data class ClientConfiguration(val serverUrl: URL, val clientId: String, val redirectUri: String) {
    constructor(env: Environment, clientId: String, redirectUri: String) : this(
        env.url,
        clientId,
        redirectUri
    )
}
