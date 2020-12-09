package com.schibsted.account.android.webflows.token

import java.util.*

data class UserSession(
    val clientId: String,
    val userTokens: UserTokens,
    val updatedAt: Date
)

