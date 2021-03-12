package com.schibsted.account.android.webflows.user

import com.schibsted.account.android.webflows.token.UserTokens
import java.util.*

internal data class StoredUserSession(
    val clientId: String,
    val userTokens: UserTokens,
    val updatedAt: Date
)
