package com.schibsted.account.webflows.token

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.schibsted.account.webflows.util.Util
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
internal data class UserTokens(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String?,
    @SerializedName("idToken")
    val idToken: String,
    @SerializedName("idTokenClaims")
    val idTokenClaims: IdTokenClaims,
) : Parcelable {
    override fun toString(): String {
        return "UserTokens(\n" +
            "accessToken: ${Util.removeJwtSignature(accessToken)},\n" +
            "refreshToken: ${Util.removeJwtSignature(refreshToken)}, \n" +
            "idToken: ${Util.removeJwtSignature(idToken)},\n" +
            "idTokenClaims: $idTokenClaims)"
    }
}
