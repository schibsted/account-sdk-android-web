package com.schibsted.account.android.webflows.user

import com.schibsted.account.android.webflows.token.UserTokens
import java.util.*

data class UserSession(
    val clientId: String,
    val userTokens: UserTokens,
    val updatedAt: Date
)

