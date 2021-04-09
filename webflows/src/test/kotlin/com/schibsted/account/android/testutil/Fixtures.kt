package com.schibsted.account.android.testutil

import android.content.Intent
import com.schibsted.account.android.webflows.api.SchibstedAccountApi
import com.schibsted.account.android.webflows.client.Client
import com.schibsted.account.android.webflows.client.ClientConfiguration
import com.schibsted.account.android.webflows.persistence.SessionStorage
import com.schibsted.account.android.webflows.persistence.StateStorage
import com.schibsted.account.android.webflows.token.IdTokenClaims
import com.schibsted.account.android.webflows.token.TokenHandler
import com.schibsted.account.android.webflows.token.UserTokens
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import java.net.URL

internal object Fixtures {
    val clientConfig = ClientConfiguration(
        URL("https://issuer.example.com"),
        "client1",
        "com.example.client://login"
    )
    val idTokenClaims = IdTokenClaims(
        clientConfig.issuer,
        "userUuid",
        "12345",
        listOf(clientConfig.clientId),
        10,
        "testNonce",
        null
    )
    val userTokens = UserTokens("accessToken", "refreshToken", "idToken", idTokenClaims)

    fun getClient(
        stateStorage: StateStorage = mockk(relaxed = true),
        sessionStorage: SessionStorage = mockk(relaxed = true),
        httpClient: OkHttpClient = Fixtures.httpClient,
        tokenHandler: TokenHandler = mockk(relaxed = true),
        schibstedAccountApi: SchibstedAccountApi = mockk(relaxed = true)
    ): Client {
        return Client(
            clientConfig,
            stateStorage,
            sessionStorage,
            httpClient,
            tokenHandler,
            schibstedAccountApi
        )
    }

    val httpClient = OkHttpClient.Builder().build()
}