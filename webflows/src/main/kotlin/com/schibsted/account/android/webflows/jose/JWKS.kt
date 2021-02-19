package com.schibsted.account.android.webflows.jose

import com.nimbusds.jose.jwk.JWKSet
import com.schibsted.account.android.webflows.api.SchibstedAccountAPI

internal interface AsyncJwks {
    fun fetch(callback: (JWKSet?) -> Unit)
}

internal class RemoteJwks(private val schibstedAccountApi: SchibstedAccountAPI) : AsyncJwks {
    override fun fetch(callback: (JWKSet?) -> Unit) {
        schibstedAccountApi.getJwks {
            it.onSuccess { jwks -> callback(jwks) }
            it.onFailure { callback(null) }
        }
    }
}
