package com.schibsted.account.android.webflows.user

import android.os.Parcelable
import com.schibsted.account.android.webflows.client.Client
import com.schibsted.account.android.webflows.token.UserTokens

import kotlinx.parcelize.Parcelize


@Parcelize
data class UserSession internal constructor(
    internal val tokens: UserTokens
) : Parcelable

class User {
    private val client: Client
    private val tokens: UserTokens

    val session: UserSession
        get() {
            return UserSession(tokens)
        }

    constructor(client: Client, session: UserSession) : this(client, session.tokens)

    internal constructor(client: Client, tokens: UserTokens) {
        this.client = client
        this.tokens = tokens
    }

    fun logout() {
        client.destroySession()
    }

    override fun toString(): String {
        return "User(uuid=${tokens.idTokenClaims.sub})"
    }

    override fun equals(other: Any?): Boolean {
        return (other is User) && tokens == other.tokens
    }
}
