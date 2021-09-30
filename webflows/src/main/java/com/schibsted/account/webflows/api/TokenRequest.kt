package com.schibsted.account.webflows.api

import com.schibsted.account.webflows.util.Util

internal data class UserTokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val id_token: String?,
    val scope: String?,
    val expires_in: Int
) {
    override fun toString(): String {
        return "UserTokenResponse(\n" +
                "access_token: ${Util.removeJwtSignature(access_token)},\n" +
                "refresh_token: ${Util.removeJwtSignature(refresh_token)}, \n" +
                "id_token: ${Util.removeJwtSignature(id_token)},\n" +
                "scope: ${scope ?: ""},\n" +
                "expires_in: ${expires_in})"
    }
}

internal data class UserTokenRequest(
    val authCode: String,
    val codeVerifier: String,
    val clientId: String,
    val redirectUri: String
)

internal data class RefreshTokenRequest(
    val refreshToken: String,
    val scope: String?,
    val clientId: String
)
