package com.schibsted.account.webflows.client

import android.content.Intent
import android.os.Build
import android.os.ConditionVariable
import androidx.test.filters.SdkSuppress
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.Fixtures.clientConfig
import com.schibsted.account.testutil.Fixtures.getClient
import com.schibsted.account.testutil.assertLeft
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.UserTokenResponse
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.persistence.StateStorage
import com.schibsted.account.webflows.persistence.StorageError
import com.schibsted.account.webflows.persistence.StorageReadCallback
import com.schibsted.account.webflows.token.TokenError
import com.schibsted.account.webflows.token.TokenHandler
import com.schibsted.account.webflows.token.TokenRequestResult
import com.schibsted.account.webflows.token.UserTokensResult
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.user.UserSession
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.concurrent.CompletableFuture

class ClientTest {
    private fun authResultIntent(authResponseParameters: String?): Intent =
        mockk {
            every { data } returns
                mockk {
                    every { query } returns authResponseParameters
                }
        }

    @Test
    fun handleAuthenticationResponseShouldReturnUserToCallback() {
        val state = "testState"
        val nonce = "testNonce"
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        val authState = AuthState(state, nonce, "codeVerifier", null)
        val stateStorageMock: StateStorage =
            mockk(relaxUnitFun = true) {
                every { getValue(Client.AUTH_STATE_KEY, AuthState::class) } returns authState
            }

        val authCode = "testAuthCode"
        val tokenHandler: TokenHandler =
            mockk(relaxed = true) {
                every { makeTokenRequest(authCode, authState, any()) } answers {
                    val callback = thirdArg<(TokenRequestResult) -> Unit>()
                    val tokensResult =
                        UserTokensResult(Fixtures.userTokens, "openid offline_access", 10)
                    callback(Right(tokensResult))
                }
            }

        val client =
            getClient(
                sessionStorage = sessionStorageMock,
                stateStorage = stateStorageMock,
                tokenHandler = tokenHandler,
            )

        client.handleAuthenticationResponse(authResultIntent("code=$authCode&state=$state")) {
            it.assertRight { user ->
                assertEquals(UserSession(Fixtures.userTokens), user.session)
            }
        }

        verify {
            sessionStorageMock.save(
                withArg { storedSession ->
                    assertEquals(clientConfig.clientId, storedSession.clientId)
                    assertEquals(Fixtures.userTokens, storedSession.userTokens)
                    val secondsSinceSessionCreated = (Date().time - storedSession.updatedAt.time) / 1000
                    assertTrue(secondsSinceSessionCreated < 1) // created within last second
                },
            )
        }
    }

    @Test
    fun handleAuthenticationResponseShouldHandleMissingIntentData() {
        getClient().handleAuthenticationResponse(authResultIntent(null)) {
            it.assertLeft { error ->
                assertEquals(LoginError.UnexpectedError("No authentication response"), error)
            }
        }
    }

    @Test
    fun handleAuthenticationResponseShouldParseTokenErrorResponse() {
        val tokenHandler: TokenHandler =
            mockk(relaxed = true) {
                every { makeTokenRequest(any(), any(), any()) } answers {
                    val callback = thirdArg<(TokenRequestResult) -> Unit>()
                    val errorResponse =
                        HttpError.ErrorResponse(
                            400,
                            """{"error": "test", "error_description": "Something went wrong"}""",
                        )
                    callback(Left(TokenError.TokenRequestError(errorResponse)))
                }
            }
        val authState = AuthState("testState", "testNonce", "codeVerifier", null)
        val stateStorageMock: StateStorage =
            mockk(relaxUnitFun = true) {
                every { getValue(Client.AUTH_STATE_KEY, AuthState::class) } returns authState
            }
        val client =
            getClient(
                tokenHandler = tokenHandler,
                stateStorage = stateStorageMock,
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
    fun handleAuthenticationResponseCanParseHtmlErrorResponse() {
        val tokenHandler: TokenHandler =
            mockk(relaxed = true) {
                every { makeTokenRequest(any(), any(), any()) } answers {
                    val callback = thirdArg<(TokenRequestResult) -> Unit>()
                    val errorResponse =
                        HttpError.ErrorResponse(
                            503,
                            """<html><body>503</body></html>""",
                        )
                    callback(Left(TokenError.TokenRequestError(errorResponse)))
                }
            }
        val authState = AuthState("testState", "testNonce", "codeVerifier", null)
        val stateStorageMock: StateStorage =
            mockk(relaxUnitFun = true) {
                every { getValue(Client.AUTH_STATE_KEY, AuthState::class) } returns authState
            }
        val client =
            getClient(
                tokenHandler = tokenHandler,
                stateStorage = stateStorageMock,
            )

        client.handleAuthenticationResponse(authResultIntent("code=authCode&state=${authState.state}")) {
            it.assertLeft { error ->
                val expected =
                    LoginError.UnexpectedError(
                        "TokenRequestError(cause=ErrorResponse(code=503, body=<html><body>503</body></html>))",
                    )
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
        val client = getClient(sessionStorage = sessionStorageMock)

        client.resumeLastLoggedInUser { result ->
            result.assertRight {
                assertEquals(
                    User(client, UserSession(Fixtures.userTokens)),
                    it,
                )
            }
        }
    }

    @Test
    fun existingSessionIsNotResumeableIfNoSessionFound() {
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        val error = StorageError.UnexpectedError(Exception("No session found."))
        every { sessionStorageMock.get(clientConfig.clientId, any()) } answers {
            val callback = secondArg<StorageReadCallback>()
            callback(Left(error))
        }
        val client = getClient(sessionStorage = sessionStorageMock)

        client.resumeLastLoggedInUser { result ->
            result.assertLeft {
                assertEquals(error, it)
            }
        }
    }

    @Test
    fun storageErrorIsPropagatedToCallback() {
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        val error = StorageError.UnexpectedError(Exception("Something went wrong"))
        every { sessionStorageMock.get(clientConfig.clientId, any()) } answers {
            val callback = secondArg<StorageReadCallback>()
            callback(Left(error))
        }
        val client = getClient(sessionStorage = sessionStorageMock)

        val resultCallback = mockk<(Either<StorageError, User?>) -> Unit>()
        every { resultCallback(any()) } just Runs

        client.resumeLastLoggedInUser(resultCallback)
        verify(exactly = 1) { resultCallback(Left(error)) }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
    @Test
    fun refreshTokensHandlesConcurrentLogout() {
        val lock = ConditionVariable(false)
        val tokenHandler =
            mockk<TokenHandler>(relaxed = true) {
                every { makeTokenRequest(any(), any()) } answers {
                    lock.block(10) // wait until after logout is complete
                    Right(UserTokenResponse("", "", "", "", 0))
                }
            }
        val client = getClient(tokenHandler = tokenHandler)
        val user = User(client, Fixtures.userTokens)

        /*
         * Run token refresh operation in separate thread, manually forcing the operation to block
         * until the user has been logged out
         */
        val refreshTask =
            CompletableFuture.supplyAsync {
                val result = client.refreshTokensForUser(user)
                result.assertLeft {
                    assertEquals(
                        RefreshTokenError.UnexpectedError("User has logged-out during token refresh"),
                        it,
                    )
                }
            }
        val logoutTask =
            CompletableFuture.supplyAsync {
                user.logout()
                lock.open() // unblock refresh token response
            }
        CompletableFuture.allOf(refreshTask, logoutTask).join()
    }

    @Test
    fun testExternalId() {
        val client = getClient()
        val externalIdAllParameters = client.getExternalId("pairId", "externalParty", "optionalSuffix")
        // values generated via : https://emn178.github.io/online-tools/sha256.html
        // pairId:externalParty:optionalSuffix
        // e0b2b31df36848059b44ac0ee6784607b003a3688ac6bbdb196d8465bbc8b281
        assertEquals(externalIdAllParameters, "e0b2b31df36848059b44ac0ee6784607b003a3688ac6bbdb196d8465bbc8b281")

        // values generated via : https://emn178.github.io/online-tools/sha256.html
        // pairId:externalParty
        // 386eb5f9c3e56843ff83e43fa3d69fc4c2b2072f8e8036332baefb04e96f28b9
        val externalIdWithoutOptionalParameters = client.getExternalId("pairId", "externalParty")
        assertEquals(externalIdWithoutOptionalParameters, "386eb5f9c3e56843ff83e43fa3d69fc4c2b2072f8e8036332baefb04e96f28b9")
    }
}
