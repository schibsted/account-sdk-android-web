package com.schibsted.account.webflows.user

import android.os.Build
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.schibsted.account.testutil.*
import com.schibsted.account.webflows.activities.AuthResultLiveData
import com.schibsted.account.webflows.activities.AuthResultLiveDataTest
import com.schibsted.account.webflows.activities.NotAuthed
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.UserTokenResponse
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.client.RefreshTokenError
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.persistence.StorageError
import com.schibsted.account.webflows.token.TokenError
import com.schibsted.account.webflows.token.TokenHandler
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.net.ConnectException
import java.util.*
import java.util.concurrent.CompletableFuture

@RunWith(AndroidJUnit4::class)
class UserTest {

    @Test
    fun `Equals fun should return true if token content is the same`() {
        val userTokens1 =
            UserTokens("accessToken", "refreshToken", "idToken", Fixtures.idTokenClaims)
        val userTokens2 =
            UserTokens("accessToken", "refreshToken", "idToken", Fixtures.idTokenClaims)
        val user1 = User(Fixtures.getClient(), userTokens1)
        val user2 = User(Fixtures.getClient(), userTokens2)
        assertTrue(user1.equals(user2))
    }

    @Test
    fun `Equals fun should return null if both users are null`() {
        val user1: User? = null
        val user2: User? = null
        assertNull(user1?.equals(user2))
    }

    @Test
    fun `Equals fun should return false is token content is different`() {
        val userTokens1 =
            UserTokens("accessToken1", "refreshToken", "idToken", Fixtures.idTokenClaims)
        val userTokens2 =
            UserTokens("accessToken2", "refreshToken", "idToken", Fixtures.idTokenClaims)
        val user1 = User(Fixtures.getClient(), userTokens1)
        val user2 = User(Fixtures.getClient(), userTokens2)
        assertFalse(user1.equals(user2))
    }

    @Test
    fun `Equals fun should return false if comparing User is null`() {
        val userTokens1 =
            UserTokens("accessToken1", "refreshToken", "idToken", Fixtures.idTokenClaims)
        val user1 = User(Fixtures.getClient(), userTokens1)
        val user2: User? = null
        assertFalse(user1.equals(user2))
    }

    @Test
    fun makeAuthenticatedRequestReturnsResponseToCallback() {
        val user = User(Fixtures.getClient(), Fixtures.userTokens)

        val responseData = "Test data"
        withServer(MockResponse().setResponseCode(200).setBody(responseData)) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertRight { assertEquals(responseData, it.body!!.string()) }
                    assertEquals(
                        "Bearer ${Fixtures.userTokens.accessToken}",
                        server.takeRequest().getHeader("Authorization")
                    )
                    done()
                }
            }
        }
    }

    @Test
    fun makeAuthenticatedRequestReturnsConnectionErrorToCallback() {
        val user = User(Fixtures.getClient(), Fixtures.userTokens)

        val request = Request.Builder().url("http://localhost:1").build()

        await { done ->
            user.makeAuthenticatedRequest(request) { result ->
                result.assertLeft { assertTrue(it is ConnectException) }
                done()
            }
        }
    }

    @Test
    fun makeAuthenticatedRequestRefreshesTokenWhen401Response() {
        val tokenHandler: TokenHandler = mockk(relaxed = true) {
            every { makeTokenRequest(any(), null) } returns run {
                val tokensResult = UserTokenResponse(
                    "newAccessToken",
                    "newRefreshToken",
                    null,
                    "openid offline_access",
                    10
                )
                Right(tokensResult)
            }
        }
        val sessionStorageMock: SessionStorage = mockk(relaxed = true) {
            every { save(any()) } returns Unit
        }
        val user =
            User(
                Fixtures.getClient(
                    tokenHandler = tokenHandler,
                    sessionStorage = sessionStorageMock
                ),
                Fixtures.userTokens
            )

        val responseData = "Test data"
        val responses = arrayOf(
            MockResponse().setResponseCode(401).setBody("Unauthorized"),
            MockResponse().setResponseCode(200).setBody(responseData)
        )
        withServer(*responses) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertRight { assertEquals(responseData, it.body!!.string()) }

                    assertEquals(
                        "Bearer ${Fixtures.userTokens.accessToken}",
                        server.takeRequest().getHeader("Authorization")
                    )
                    assertEquals(
                        "Bearer newAccessToken",
                        server.takeRequest().getHeader("Authorization")
                    )

                    verify {
                        sessionStorageMock.save(withArg { storedSession ->
                            assertEquals(Fixtures.clientConfig.clientId, storedSession.clientId)
                            assertEquals(
                                Fixtures.userTokens.copy(
                                    accessToken = "newAccessToken",
                                    "newRefreshToken"
                                ), storedSession.userTokens
                            )
                            val secondsSinceSessionCreated =
                                (Date().time - storedSession.updatedAt.time) / 1000
                            assertTrue(secondsSinceSessionCreated < 1) // created within last second
                        })
                    }
                    done()
                }
            }
        }
    }


    @Test
    fun makeAuthenticatedRequestDoesntRefreshOnRepeated401() {
        val tokenHandler: TokenHandler = mockk(relaxed = true) {
            every { makeTokenRequest(any(), null) } returns run {
                val tokensResult = UserTokenResponse(
                    "newAccessToken",
                    "newRefreshToken",
                    null,
                    "openid offline_access",
                    10
                )
                Right(tokensResult)
            }
        }
        val user = User(Fixtures.getClient(tokenHandler = tokenHandler), Fixtures.userTokens)

        val responses = arrayOf(
            MockResponse().setResponseCode(401).setBody("Unauthorized"),
            MockResponse().setResponseCode(401).setBody("Still unauthorized"),
        )
        withServer(*responses) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertRight { assertEquals("Still unauthorized", it.body!!.string()) }

                    assertEquals(
                        "Bearer ${Fixtures.userTokens.accessToken}",
                        server.takeRequest().getHeader("Authorization")
                    )
                    assertEquals(
                        "Bearer newAccessToken",
                        server.takeRequest().getHeader("Authorization")
                    )

                    // only tries to refresh once
                    verify(exactly = 1) {
                        tokenHandler.makeTokenRequest(
                            Fixtures.userTokens.refreshToken!!,
                            null
                        )
                    }
                    done()
                }
            }
        }
    }

    @Test
    fun makeAuthenticatedRequestForwards401ResponseWhenNoRefreshToken() {
        val user = User(Fixtures.getClient(), Fixtures.userTokens.copy(refreshToken = null))

        withServer(MockResponse().setResponseCode(401).setBody("Unauthorized")) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertRight { assertEquals("Unauthorized", it.body!!.string()) }
                    assertEquals(
                        "Bearer ${Fixtures.userTokens.accessToken}",
                        server.takeRequest().getHeader("Authorization")
                    )

                    done()
                }
            }
        }
    }


    @Test
    fun makeAuthenticatedRequestForwardsOriginalResponseWhenTokenRefreshFails() {
        val tokenHandler: TokenHandler = mockk(relaxed = true) {
            every { makeTokenRequest(any(), null) } returns run {
                val error =
                    TokenError.TokenRequestError(HttpError.UnexpectedError(Error("Refresh token request failed")))
                Left(error)
            }
        }
        val user = User(Fixtures.getClient(tokenHandler = tokenHandler), Fixtures.userTokens)

        withServer(MockResponse().setResponseCode(401).setBody("Unauthorized")) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertRight { assertEquals("Unauthorized", it.body!!.string()) }
                    assertEquals(
                        "Bearer ${Fixtures.userTokens.accessToken}",
                        server.takeRequest().getHeader("Authorization")
                    )

                    verify(exactly = 1) {
                        tokenHandler.makeTokenRequest(Fixtures.userTokens.refreshToken!!, null)
                    }

                    done()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    @Test
    fun refreshTokensOnlyRefreshesOnceWhenConcurrentCalls() {
        val client: Client = mockk(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.refreshTokensForUser(user) } answers {
            Thread.sleep(20) // artificial delay to simulate network request
            Right(Fixtures.userTokens.copy(accessToken = "accessToken1"))
        } andThen {
            Right(Fixtures.userTokens.copy(accessToken = "accessToken2"))
        }

        val refreshTask = {
            val result = user.refreshTokens()
            result.assertRight { assertEquals("accessToken1", it.accessToken) }
        }

        val refreshTask1 = CompletableFuture.supplyAsync(refreshTask)
        val refreshTask2 = CompletableFuture.supplyAsync(refreshTask)
        CompletableFuture.allOf(refreshTask1, refreshTask2).join()
        verify(exactly = 1) { client.refreshTokensForUser(user) }
    }

    @Test
    fun refreshTokensLogsOutOnInvalidGrantResponse() {
        val client: Client = mockk(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        val tokenRefreshResponse = Left(
            RefreshTokenError.RefreshRequestFailed(
                HttpError.ErrorResponse(
                    400,
                    """{"error": "invalid_grant", "error_description": "Invalid refresh token"}"""
                )
            )
        )
        every { client.refreshTokensForUser(user) } returns tokenRefreshResponse

        assertEquals(Left(RefreshTokenError.UserWasLoggedOut), user.refreshTokens())
        assertNull(user.tokens)
    }

    @Test
    fun refreshTokensDoesNotCauseLogoutOn500Response() {
        val client: Client = mockk(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        val tokenRefreshResponse = Left(
            RefreshTokenError.RefreshRequestFailed(
                HttpError.ErrorResponse(
                    500,
                    """<html>Error</html>"""
                )
            )
        )
        every { client.refreshTokensForUser(user) } returns tokenRefreshResponse

        assertEquals(tokenRefreshResponse, user.refreshTokens())
    }

    @Test
    fun logoutDestroysTokensAndSession() {
        val sessionStorageMock: SessionStorage = mockk(relaxUnitFun = true)
        val client = Fixtures.getClient(sessionStorage = sessionStorageMock)
        val user = User(client, UserSession(Fixtures.userTokens))

        user.logout()
        assertNull(user.tokens)
        assertFalse(user.isLoggedIn())
        val exc = assertThrows(IllegalStateException::class.java) { user.session }
        assertEquals("Can not use tokens of logged-out user!", exc.message)
        verify { sessionStorageMock.remove(Fixtures.clientConfig.clientId) }
    }

    @Test
    fun logoutUpdatesAuthResultLiveData() {
        val client = mockk<Client>(relaxed = true)
        val user = User(client, Fixtures.userTokens)
        every { client.resumeLastLoggedInUser(any()) } answers {
            val callback = firstArg<(Either<StorageError, User?>) -> Unit>()
            callback(Right(user))
        }
        AuthResultLiveData.create(client)

        AuthResultLiveData.get().logout()
        shadowOf(Looper.getMainLooper()).idle()
        AuthResultLiveData.get().value!!.assertLeft { assertEquals(NotAuthed.NoLoggedInUser, it) }

        AuthResultLiveDataTest.resetInstance()
    }

    @Test
    fun accountPagesUrlIsCorrect() {
        val user = User(Fixtures.getClient(), Fixtures.userTokens)
        assertEquals(
            "${Fixtures.clientConfig.serverUrl}/account/summary",
            user.accountPagesUrl().toString()
        )
    }
}
