package com.schibsted.account.webflows.jose

import com.nimbusds.jose.jwk.JWKSet
import com.schibsted.account.webflows.api.SchibstedAccountApi
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right

internal interface AsyncJwks {
    suspend fun fetch(): JWKSet?
}

internal class RemoteJwks(private val schibstedAccountApi: SchibstedAccountApi) : AsyncJwks {
    override suspend fun fetch(): JWKSet? {
        val result = schibstedAccountApi.getJwks()
        return when (result) {
            is Right -> result.value
            is Left -> null
        }
    }
}
