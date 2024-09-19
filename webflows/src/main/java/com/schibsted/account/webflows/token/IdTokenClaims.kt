package com.schibsted.account.webflows.token

import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
internal data class IdTokenClaims(
    @SerializedName("iss")
    val iss: String,
    @SerializedName("sub")
    val sub: String,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("aud")
    val aud: List<String>,
    @SerializedName("exp")
    val exp: Long,
    @SerializedName("nonce")
    val nonce: String?,
    @SerializedName("amr")
    val amr: List<String>?,
) : Parcelable
