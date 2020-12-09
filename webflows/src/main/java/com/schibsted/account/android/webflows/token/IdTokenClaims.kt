package com.schibsted.account.android.webflows.token

data class IdTokenClaims(
    val iss: String,
    val sub: String,
    val aud: Array<String>,
    val exp: Double, // ??
    val nonce: String?,
    val amr: Array<String>
)