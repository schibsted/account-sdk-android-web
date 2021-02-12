package com.schibsted.account.android.webflows.client

import io.mockk.mockk
import org.junit.Assert
import org.junit.Test
import java.net.URL
import java.net.URLDecoder


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
            mockk(relaxed = true)
        )
        val queryParams = getQueryParameters(URL(client.generateLoginUrl()))

        Assert.assertEquals(config.clientId, queryParams["client_id"])
        Assert.assertEquals(config.redirectUri, queryParams["redirect_uri"])
        Assert.assertEquals("code", queryParams["response_type"],)
        Assert.assertEquals("select_account", queryParams["prompt"])
        Assert.assertEquals(
            setOf("openid", "offline_access"),
            queryParams.getValue("scope").split(" ").toSet()
        )
        Assert.assertNotNull(queryParams["state"])
        Assert.assertNotNull(queryParams["nonce"])
        Assert.assertNotNull(queryParams["code_challenge"])
        Assert.assertEquals("S256", queryParams["code_challenge_method"],)
    }

    @Test
    fun loginUrlShouldContainExtraScopesSpecified() {
        val client = Client(
            config,
            mockk(relaxed = true)
        )
        val loginUrl = client.generateLoginUrl(extraScopeValues = setOf("scope1", "scope2"))
        val queryParams = getQueryParameters(URL(loginUrl))

        Assert.assertEquals(config.clientId, queryParams["client_id"])
        Assert.assertEquals(config.redirectUri, queryParams["redirect_uri"])
        Assert.assertEquals("code", queryParams["response_type"],)
        Assert.assertEquals("select_account", queryParams["prompt"])
        Assert.assertEquals(
            setOf("openid", "offline_access", "scope1", "scope2"),
            queryParams.getValue("scope").split(" ").toSet()
        )
        Assert.assertNotNull(queryParams["state"])
        Assert.assertNotNull(queryParams["nonce"])
        Assert.assertNotNull(queryParams["code_challenge"])
        Assert.assertEquals("S256", queryParams["code_challenge_method"])
    }

    @Test
    fun loginUrlForMfaShouldContainAcrValues() {
        val client = Client(
            config,
            mockk(relaxed = true)
        )
        val loginUrl = client.generateLoginUrl(mfa = MfaType.OTP)
        val queryParams = getQueryParameters(URL(loginUrl))

        Assert.assertEquals(config.clientId, queryParams["client_id"])
        Assert.assertEquals(config.redirectUri, queryParams["redirect_uri"])
        Assert.assertEquals("code", queryParams["response_type"],)
        Assert.assertNull(queryParams["prompt"])
        Assert.assertEquals(
            setOf("openid", "offline_access"),
            queryParams.getValue("scope").split(" ").toSet()
        )
        Assert.assertNotNull(queryParams["state"])
        Assert.assertNotNull(queryParams["nonce"])
        Assert.assertNotNull(queryParams["code_challenge"])
        Assert.assertEquals("S256", queryParams["code_challenge_method"],)
    }

    private fun getQueryParameters(url: URL): Map<String, String> {
        return url.query.split("&").map {
            val splitted = it.split("=")
            val key = URLDecoder.decode(splitted[0], "UTF-8")
            val value = URLDecoder.decode(splitted[1], "UTF-8")
            key to value
        }.toMap()
    }
}