package com.schibsted.account.webflows.user

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.schibsted.account.webflows.token.UserTokens
import java.util.Date

@Keep
internal data class StoredUserSession(
    @SerializedName("clientId")
    val clientId: String,
    @SerializedName("userTokens")
    val userTokens: UserTokens,
    @SerializedName("updatedAt")
    val updatedAt: Date
)