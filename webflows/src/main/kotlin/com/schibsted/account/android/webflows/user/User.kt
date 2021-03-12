package com.schibsted.account.android.webflows.user

import com.schibsted.account.android.webflows.client.Client

class User {
    private val client: Client
    val session: UserSession

    constructor(client: Client, userSession: UserSession) {
        this.client = client
        this.session = userSession
    }

    fun logout() {
        client.destroySession()
    }

    override fun toString(): String {
        return "User(uuid=${session.userTokens.idTokenClaims.sub})"
    }

    override fun equals(other: Any?): Boolean {
        return (other is User) && session.userTokens == other.session.userTokens
    }
}
