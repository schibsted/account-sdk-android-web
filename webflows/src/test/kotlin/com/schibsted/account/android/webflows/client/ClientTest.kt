package com.schibsted.account.android.webflows.client

import com.schibsted.account.android.webflows.MfaType
import com.schibsted.account.android.webflows.util.Util
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.net.URL


class ClientTest {
    private val config =
        ClientConfiguration(
            URL("https://issuer.example.com"),
            "client1",
            "com.example.client://login"
        )

    @Test
    fun loginUrlShouldBeCorrect() {
        val client = Client(
            config,
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
        val queryParams = Util.parseQueryParameters(URL(client.generateLoginUrl()).query)

        assertEquals(config.clientId, queryParams["client_id"])
        assertEquals(config.redirectUri, queryParams["redirect_uri"])
        assertEquals("code", queryParams["response_type"],)
        assertEquals("select_account", queryParams["prompt"])
        assertEquals(
            setOf("openid", "offline_access"),
            queryParams.getValue("scope").split(" ").toSet()
        )
        assertNotNull(queryParams["state"])
        assertNotNull(queryParams["nonce"])
        assertNotNull(queryParams["code_challenge"])
        assertEquals("S256", queryParams["code_challenge_method"],)
    }

    @Test
    fun loginUrlShouldContainExtraScopesSpecified() {
        val client = Client(
            config,
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
        val loginUrl = client.generateLoginUrl(extraScopeValues = setOf("scope1", "scope2"))
        val queryParams = Util.parseQueryParameters((URL(loginUrl).query))

        assertEquals(config.clientId, queryParams["client_id"])
        assertEquals(config.redirectUri, queryParams["redirect_uri"])
        assertEquals("code", queryParams["response_type"],)
        assertEquals("select_account", queryParams["prompt"])
        assertEquals(
            setOf("openid", "offline_access", "scope1", "scope2"),
            queryParams.getValue("scope").split(" ").toSet()
        )
        assertNotNull(queryParams["state"])
        assertNotNull(queryParams["nonce"])
        assertNotNull(queryParams["code_challenge"])
        assertEquals("S256", queryParams["code_challenge_method"])
    }

    @Test
    fun loginUrlForMfaShouldContainAcrValues() {
        val client = Client(
            config,
            mockk(relaxed = true),
            mockk(relaxed = true)
        )
        val loginUrl = client.generateLoginUrl(mfa = MfaType.OTP)
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals(config.clientId, queryParams["client_id"])
        assertEquals(config.redirectUri, queryParams["redirect_uri"])
        assertEquals("code", queryParams["response_type"],)
        assertNull(queryParams["prompt"])
        assertEquals(
            setOf("openid", "offline_access"),
            queryParams.getValue("scope").split(" ").toSet()
        )
        assertNotNull(queryParams["state"])
        assertNotNull(queryParams["nonce"])
        assertNotNull(queryParams["code_challenge"])
        assertEquals("S256", queryParams["code_challenge_method"],)
    }
}
