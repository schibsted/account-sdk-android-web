package com.schibsted.account.android.webflows.client

import android.content.Intent
import android.util.Log
import com.schibsted.account.android.testutil.Fixtures
import com.schibsted.account.android.testutil.Fixtures.clientConfig
import com.schibsted.account.android.testutil.Fixtures.getClient
import com.schibsted.account.android.testutil.assertLeft
import com.schibsted.account.android.testutil.assertRight
import com.schibsted.account.android.webflows.AuthState
import com.schibsted.account.android.webflows.MfaType
import com.schibsted.account.android.webflows.persistence.SessionStorage
import com.schibsted.account.android.webflows.persistence.StateStorage
import com.schibsted.account.android.webflows.token.TokenHandler
import com.schibsted.account.android.webflows.token.TokenRequestResult
import com.schibsted.account.android.webflows.token.UserTokensResult
import com.schibsted.account.android.webflows.user.StoredUserSession
import com.schibsted.account.android.webflows.user.User
import com.schibsted.account.android.webflows.user.UserSession
import com.schibsted.account.android.webflows.util.Either.Right
import com.schibsted.account.android.webflows.util.Util
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.net.URL
import java.util.*

class ClientTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            mockkStatic(Log::class)
            every { Log.v(any(), any()) } returns 0
            every { Log.d(any(), any()) } returns 0
            every { Log.i(any(), any()) } returns 0
            every { Log.e(any(), any()) } returns 0
        }
    }

    private fun authResultIntent(authResponseParameters: String?): Intent {
        return mockk {
            every { data } returns mockk {
                every { query } returns authResponseParameters
            }
        }
    }

    @Test
    fun loginUrlShouldBeCorrect() {
        val client = getClient()
        val queryParams = Util.parseQueryParameters(URL(client.generateLoginUrl()).query)

        assertEquals(clientConfig.clientId, queryParams["client_id"])
        assertEquals(clientConfig.redirectUri, queryParams["redirect_uri"])
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
    fun loginUrlShouldContainExtraScopesSpecified() {
        val client = getClient()
        val loginUrl = client.generateLoginUrl(extraScopeValues = setOf("scope1", "scope2"))
        val queryParams = Util.parseQueryParameters((URL(loginUrl).query))

        assertEquals(clientConfig.clientId, queryParams["client_id"])
        assertEquals(clientConfig.redirectUri, queryParams["redirect_uri"])
        assertEquals("code", queryParams["response_type"])
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
        val client = getClient()
        val loginUrl = client.generateLoginUrl(mfa = MfaType.OTP)
        val queryParams = Util.parseQueryParameters(URL(loginUrl).query)

        assertEquals(clientConfig.clientId, queryParams["client_id"])
        assertEquals(clientConfig.redirectUri, queryParams["redirect_uri"])
        assertEquals("code", queryParams["response_type"])
        assertNull(queryParams["prompt"])
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
    fun handleAuthenticationResponseShouldReturnUserToCallback() {
        val state = "testState"
        val nonce = "testNonce"
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        val authState = AuthState(state, nonce, "codeVerifier", null)
        val stateStorageMock: StateStorage = mockk(relaxUnitFun = true) {
            every { getValue(Client.AUTH_STATE_KEY, AuthState::class) } returns authState
        }

        val authCode = "testAuthCode"
        val tokenHandler: TokenHandler = mockk(relaxed = true) {
            every { makeTokenRequest(authCode, authState, any()) } answers {
                val callback = thirdArg<(TokenRequestResult) -> Unit>()
                val tokensResult =
                    UserTokensResult(Fixtures.userTokens, "openid offline_access", 10)
                callback(Right(tokensResult))
            }
        }

        val client = getClient(
            sessionStorage = sessionStorageMock,
            stateStorage = stateStorageMock,
            tokenHandler = tokenHandler
        )

        client.handleAuthenticationResponse(authResultIntent("code=$authCode&state=$state")) {
            it.assertRight { user ->
                assertEquals(UserSession(Fixtures.userTokens), user.session)
            }
        }

        verify {
            sessionStorageMock.save(withArg { storedSession ->
                assertEquals(clientConfig.clientId, storedSession.clientId)
                assertEquals(Fixtures.userTokens, storedSession.userTokens)
                val secondsSinceSessionCreated = (Date().time - storedSession.updatedAt.time) / 1000
                assertTrue(secondsSinceSessionCreated < 1) // created within last second
            })
        }
    }

    @Test
    fun handleAuthenticationResponseShouldHandleMissingIntentData() {
        getClient().handleAuthenticationResponse(authResultIntent(null)) {
            it.assertLeft { error ->
                when (error) {
                    is LoginError.UnexpectedError -> {
                        assertEquals("No authentication response", error.message)
                    }
                    else -> {
                        fail("Incorrect error: $error")
                    }
                }
            }
        }
    }

    @Test
    fun logoutDeletesSessionFromStorage() {
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        val client = getClient(sessionStorage = sessionStorageMock)

        User(client, UserSession(Fixtures.userTokens)).logout()
        verify { sessionStorageMock.remove(clientConfig.clientId) }
    }

    @Test
    fun existingSessionIsResumeable() {
        val userSession = StoredUserSession(clientConfig.clientId, Fixtures.userTokens, Date())
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        every { sessionStorageMock.get(clientConfig.clientId) } returns userSession
        val client = getClient(sessionStorage = sessionStorageMock)

        assertEquals(
            User(client, UserSession(Fixtures.userTokens)),
            client.resumeLastLoggedInUser()
        )
    }
}
