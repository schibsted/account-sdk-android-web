package com.schibsted.account.webflows.user

import com.schibsted.account.webflows.token.UserTokens
import java.util.Date

internal data class StoredUserSession(
    val clientId: String,
    val userTokens: UserTokens,
    val updatedAt: Date
)
