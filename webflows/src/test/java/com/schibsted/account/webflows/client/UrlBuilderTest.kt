package com.schibsted.account.webflows.client

import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.webflows.util.Util
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class UrlBuilderTest {
    private fun getUrlBuilder(): UrlBuilder {
        return UrlBuilder(Fixtures.clientConfig, mockk(relaxed = true), Client.AUTH_STATE_KEY)
    }

    @Test
    fun loginUrlShouldBeCorrect() {
        val loginUrl = getUrlBuilder().loginUrl(AuthRequest())
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals(Fixtures.clientConfig.clientId, queryParams["client_id"])
        assertEquals(Fixtures.clientConfig.redirectUri, queryParams["redirect_uri"])
        assertEquals("code", queryParams["response_type"])
        assertEquals("select_account", queryParams["prompt"])
        assertEquals(
            setOf("openid", "offline_access"),
            queryParams.getValue("scope").split(" ").toSet()
        )
        assertNotNull(queryParams["state"])
        assertNotNull(queryParams["nonce"])
        assertNotNull(queryParams["code_challenge"])
        assertEquals("S256", queryParams["code_challenge_method"])
    }

    @Test
    fun loginUrlShouldContainLoginHintIfSpecified() {
        val loginUrl = getUrlBuilder().loginUrl(AuthRequest(loginHint = "test@example.com"))
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals("test@example.com", queryParams["login_hint"])
    }

    @Test
    fun loginUrlShouldContainExtraScopesSpecified() {
        val loginUrl = getUrlBuilder().loginUrl(AuthRequest(extraScopeValues = setOf("scope1", "scope2")))
        val queryParams = Util.parseQueryParameters((URL(loginUrl).query))

        assertEquals(
            setOf("openid", "offline_access", "scope1", "scope2"),
            queryParams.getValue("scope").split(" ").toSet()
        )
    }

    @Test
    fun loginUrlForMfaShouldContainAcrValues() {
        val loginUrl = getUrlBuilder().loginUrl(AuthRequest(mfa = MfaType.OTP))
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertNull(queryParams["prompt"])
        assertEquals(MfaType.OTP.value, queryParams["acr_values"])
    }

    @Test
    fun loginUrlShouldContainCustomStateSpecified() {
        val loginUrl = getUrlBuilder().loginUrl(AuthRequest(), "customState")
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals("customState", queryParams["state"])
    }
}
