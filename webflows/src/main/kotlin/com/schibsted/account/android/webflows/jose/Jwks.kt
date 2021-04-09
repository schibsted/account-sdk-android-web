package com.schibsted.account.android.webflows.jose

import com.nimbusds.jose.jwk.JWKSet
import com.schibsted.account.android.webflows.api.SchibstedAccountApi

internal interface AsyncJwks {
    fun fetch(callback: (JWKSet?) -> Unit)
}

internal class RemoteJwks(private val schibstedAccountApi: SchibstedAccountApi) : AsyncJwks {
    override fun fetch(callback: (JWKSet?) -> Unit) {
        schibstedAccountApi.getJwks {
            it
                .foreach { jwks -> callback(jwks) }
                .left().foreach { callback(null) }
        }
    }
}
