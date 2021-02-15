package com.schibsted.account.android.webflows.api

data class UserTokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val id_token: String?,
    val scope: String?,
    val expires_in: Int
)
