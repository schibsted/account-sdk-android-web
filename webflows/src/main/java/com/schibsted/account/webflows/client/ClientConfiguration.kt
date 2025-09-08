package com.schibsted.account.webflows.client

import java.net.URL

data class ClientConfiguration(
    val serverUrl: URL,
    val clientId: String,
    val redirectUri: String,
    val skipLegacySessionMigration: Boolean = false,
) {
    val issuer: String = serverUrl.toString()

    constructor(
        env: Environment,
        clientId: String,
        redirectUri: String,
        skipLegacySessionMigration: Boolean = false,
    ) : this(
        env.url,
        clientId,
        redirectUri,
        skipLegacySessionMigration,
    )
}
