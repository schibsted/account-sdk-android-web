package com.schibsted.account.android.webflows.token

import kotlin.jvm.Throws

object IdTokenValidator {

    //https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation
    @Throws(TokenValidationError::class)
    fun validate(idToken: String, validationContext: IdTokenValidationContext) {
        //TODO: Implement
    }

}

data class IdTokenValidationContext(
    val issuer: String,
    val clientId: String,
    val nonce: String?,
    val expectedAMR: String

)
