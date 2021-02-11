package com.schibsted.account.android.webflows.token

data class UserTokens (
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String,
    val idTokenClaims: IdTokenClaims
)
