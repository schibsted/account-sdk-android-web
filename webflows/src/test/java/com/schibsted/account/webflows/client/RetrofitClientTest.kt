package com.schibsted.account.webflows.client

import android.content.Intent
import android.os.Build
import android.os.ConditionVariable
import android.util.Log
import androidx.annotation.RequiresApi
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.Fixtures.clientConfig
import com.schibsted.account.testutil.Fixtures.getRetrofitClient
import com.schibsted.account.testutil.assertLeft
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.UserTokenResponse
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.persistence.StateStorage
import com.schibsted.account.webflows.persistence.StorageReadCallback
import com.schibsted.account.webflows.token.TokenError
import com.schibsted.account.webflows.token.TokenHandler
import com.schibsted.account.webflows.token.TokenRequestResult
import com.schibsted.account.webflows.token.UserTokensResult
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.user.UserSession
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.util.*
import java.util.concurrent.CompletableFuture

class RetrofitClientTest {
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

        val client = getRetrofitClient(
            Fixtures.getClient(
                sessionStorage = sessionStorageMock,
                stateStorage = stateStorageMock,
                tokenHandler = tokenHandler
            )
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
        getRetrofitClient(Fixtures.getClient()).handleAuthenticationResponse(authResultIntent(null)) {
            it.assertLeft { error ->
                assertEquals(LoginError.UnexpectedError("No authentication response"), error)
            }
        }
    }

    @Test
    fun handleAuthenticationResponseShouldParseTokenErrorResponse() {
        val tokenHandler: TokenHandler = mockk(relaxed = true) {
            every { makeTokenRequest(any(), any(), any()) } answers {
                val callback = thirdArg<(TokenRequestResult) -> Unit>()
                val errorResponse = HttpError.ErrorResponse(
                    400,
                    """{"error": "test", "error_description": "Something went wrong"}"""
                )
                callback(Left(TokenError.TokenRequestError(errorResponse)))
            }
        }
        val authState = AuthState("testState", "testNonce", "codeVerifier", null)
        val stateStorageMock: StateStorage = mockk(relaxUnitFun = true) {
            every { getValue(Client.AUTH_STATE_KEY, AuthState::class) } returns authState
        }
        val client = getRetrofitClient(
            Fixtures.getClient(
                tokenHandler = tokenHandler,
                stateStorage = stateStorageMock
            )
        )

        client.handleAuthenticationResponse(authResultIntent("code=authCode&state=${authState.state}")) {
            it.assertLeft { error ->
                val expected =
                    LoginError.TokenErrorResponse(OAuthError("test", "Something went wrong"))
                assertEquals(expected, error)
            }
        }
    }

    @Test
    fun existingSessionIsResumeable() {
        val userSession = StoredUserSession(clientConfig.clientId, Fixtures.userTokens, Date())
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        every { sessionStorageMock.get(clientConfig.clientId, any()) } answers {
            val callback = secondArg<StorageReadCallback>()
            callback(Right(userSession))
        }
        val client = getRetrofitClient(Fixtures.getClient(sessionStorage = sessionStorageMock))

        client.resumeLastLoggedInUser { result ->
            result.assertRight {
                assertEquals(
                    User(client.internalClient, UserSession(Fixtures.userTokens)),
                    it
                )
            }
        }
    }

    @Test
    fun `IsInitialized should return false if retrofitClient has not been initialized`() {
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        val client = getRetrofitClient(Fixtures.getClient(sessionStorage = sessionStorageMock))

        val result = client.isInitialized()
        assertFalse(result)
    }

    @Test
    fun `IsInitialized should return true if retrofitClient has been initialized`() {
        val userSession = StoredUserSession(clientConfig.clientId, Fixtures.userTokens, Date())
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        every { sessionStorageMock.get(clientConfig.clientId, any()) } answers {
            val callback = secondArg<StorageReadCallback>()
            callback(Right(userSession))
        }
        val client = getRetrofitClient(Fixtures.getClient(sessionStorage = sessionStorageMock))

        client.resumeLastLoggedInUser { result ->
            result.assertRight {
                assertEquals(
                    User(client.internalClient, UserSession(Fixtures.userTokens)),
                    it
                )
            }
        }

        val result = client.isInitialized()
        assertTrue(result)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Test
    fun refreshTokensHandlesConcurrentLogout() {
        val lock = ConditionVariable(false)
        val tokenHandler = mockk<TokenHandler>(relaxed = true) {
            every { makeTokenRequest(any(), any()) } answers {
                lock.block(10) // wait until after logout is complete
                Right(UserTokenResponse("", "", "", "", 0))
            }
        }
        val client = getRetrofitClient(Fixtures.getClient(tokenHandler = tokenHandler))
        val user = User(client.internalClient, Fixtures.userTokens)

        /*
         * Run token refresh operation in separate thread, manually forcing the operation to block
         * until the user has been logged out
         */
        val refreshTask = CompletableFuture.supplyAsync {
            val result = client.refreshTokensForUser(user)
            result.assertLeft {
                assertEquals(
                    RefreshTokenError.UnexpectedError("User has logged-out during token refresh"),
                    it
                )
            }
        }
        val logoutTask = CompletableFuture.supplyAsync {
            user.logout()
            lock.open() // unblock refresh token response
        }
        CompletableFuture.allOf(refreshTask, logoutTask).join()
    }
}
