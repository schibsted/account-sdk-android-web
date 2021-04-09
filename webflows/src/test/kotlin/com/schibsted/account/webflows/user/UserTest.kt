package com.schibsted.account.webflows.user

import android.util.Log
import com.schibsted.account.testutil.*
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.UserTokenResponse
import com.schibsted.account.webflows.client.Client
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.token.TokenError
import com.schibsted.account.webflows.token.TokenHandler
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.net.ConnectException
import java.util.*
import kotlin.concurrent.thread

class UserTest {
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

        val t1 = thread {
            val result = user.refreshTokens()
            result.assertRight { assertEquals("accessToken1", it.accessToken) }
        }
        val t2 = thread {
            val result = user.refreshTokens()
            result.assertRight { assertEquals("accessToken1", it.accessToken) }
        }

        t1.join()
        t2.join()
        verify(exactly = 1) { client.refreshTokensForUser(user) }
    }
}
