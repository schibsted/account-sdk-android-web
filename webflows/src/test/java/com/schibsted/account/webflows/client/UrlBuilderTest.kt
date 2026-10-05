package com.schibsted.account.webflows.client

import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.webflows.util.Util
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URL
import java.util.UUID

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
            queryParams.getValue("scope").split(" ").toSet(),
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
            queryParams.getValue("scope").split(" ").toSet(),
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

    @Test
    fun loginUrlShouldContainXDomainId() {
        val uuid = UUID.fromString("03142019-c75d-4130-8ce4-aea8314ce949")
        val loginUrl = getUrlBuilder().loginUrl(AuthRequest(xDomainId = uuid), "customState")
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals("03142019-c75d-4130-8ce4-aea8314ce949", queryParams["x_domain_id"])
    }

    @Test
    fun loginUrlShouldContainConsents() {
        val loginUrl = getUrlBuilder().loginUrl(
            AuthRequest(
                consents = SchibstedAccountConsents(
                    advertising = SchibstedAccountConsents.Status.ACCEPTED,
                    analytics = SchibstedAccountConsents.Status.ACCEPTED,
                    marketing = SchibstedAccountConsents.Status.ACCEPTED,
                    personalization = SchibstedAccountConsents.Status.ACCEPTED,
                ),
            ),
            "customState"
        )
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals("advertising,analytics,marketing,personalization", queryParams["consents"])
        assertEquals("v1", queryParams["consent_version"])
    }

    @Test
    fun loginUrlShouldContainPartialConsents() {
        val loginUrl = getUrlBuilder().loginUrl(
            AuthRequest(
                consents = SchibstedAccountConsents(
                    advertising = SchibstedAccountConsents.Status.ACCEPTED,
                    analytics = SchibstedAccountConsents.Status.ACCEPTED,
                    marketing = SchibstedAccountConsents.Status.REJECTED,
                    personalization = SchibstedAccountConsents.Status.UNKNOWN,
                ),
            ),
            "customState"
        )
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals("advertising,analytics", queryParams["consents"])
    }

    @Test
    fun loginUrlShouldContainRejectedConsents() {
        val loginUrl = getUrlBuilder().loginUrl(
            AuthRequest(
                consents = SchibstedAccountConsents(
                    advertising = SchibstedAccountConsents.Status.REJECTED,
                    analytics = SchibstedAccountConsents.Status.REJECTED,
                    marketing = SchibstedAccountConsents.Status.REJECTED,
                    personalization = SchibstedAccountConsents.Status.REJECTED,
                ),
            ),
            "customState"
        )
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals("rejected", queryParams["consents"])
    }

    @Test
    fun loginUrlShouldNotContainEmptyConsents() {
        val loginUrl = getUrlBuilder().loginUrl(
            AuthRequest(
                consents = null,
            ),
            "customState"
        )
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals(null, queryParams["consents"])
    }
}
