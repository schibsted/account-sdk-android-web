package com.schibsted.account.testutil

import com.schibsted.account.webflows.api.SchibstedAccountApi
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.ClientConfiguration
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.persistence.StateStorage
import com.schibsted.account.webflows.token.IdTokenClaims
import com.schibsted.account.webflows.token.TokenHandler
import com.schibsted.account.webflows.token.UserTokens
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
