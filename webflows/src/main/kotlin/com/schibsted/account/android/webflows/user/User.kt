package com.schibsted.account.android.webflows.user

import com.schibsted.account.android.webflows.client.Client

class User {
    private val client: Client
    private val userSession: UserSession

    internal constructor(client: Client, userSession: UserSession) {
        this.client = client
        this.userSession = userSession
    }

    override fun toString(): String {
        return "User(uuid=${userSession.userTokens.idTokenClaims.sub})"
    }
}
