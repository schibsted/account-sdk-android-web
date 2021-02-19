package com.schibsted.account.android.webflows.token

import com.schibsted.account.android.webflows.util.Util

internal data class UserTokens(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String,
    val idTokenClaims: IdTokenClaims,
) {
    override fun toString(): String {
        return "UserTokens(\n" +
                "accessToken: ${Util.removeJwtSignature(accessToken)},\n" +
                "refreshToken: ${Util.removeJwtSignature(refreshToken)}, \n" +
                "idToken: ${Util.removeJwtSignature(idToken)},\n" +
                "idTokenClaims: ${idTokenClaims})"
    }
}
