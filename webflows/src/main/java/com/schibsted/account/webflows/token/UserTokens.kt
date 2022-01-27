package com.schibsted.account.webflows.token

import android.os.Parcelable
import com.schibsted.account.webflows.util.Util
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class UserTokens(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String,
    val idTokenClaims: IdTokenClaims
) : Parcelable {
    override fun toString(): String {
        return "UserTokens(\n" +
                "accessToken: ${Util.removeJwtSignature(accessToken)},\n" +
                "refreshToken: ${Util.removeJwtSignature(refreshToken)}, \n" +
                "idToken: ${Util.removeJwtSignature(idToken)},\n" +
                "idTokenClaims: ${idTokenClaims})"
    }
}

internal data class MigrationUserTokens(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String?,
    val idTokenClaims: IdTokenClaims?
)
