package com.schibsted.account.webflows.token

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class IdTokenClaims(
    val iss: String,
    val sub: String,
    val userId: String,
    val aud: List<String>,
    val exp: Long,
    val nonce: String?,
    val amr: List<String>?
) : Parcelable
