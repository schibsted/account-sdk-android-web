package com.schibsted.account.android.webflows.user

import android.os.Parcelable
import com.schibsted.account.android.webflows.token.UserTokens
import kotlinx.parcelize.Parcelize
import java.util.*

@Parcelize
data class UserSession internal constructor(
    val clientId: String,
    internal val userTokens: UserTokens,
    val updatedAt: Date
) : Parcelable
