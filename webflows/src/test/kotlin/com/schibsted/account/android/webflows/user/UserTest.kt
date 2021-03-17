package com.schibsted.account.android.webflows.user

import Fixtures
import android.util.Log
import assertError
import assertSuccess
import await
import com.schibsted.account.android.webflows.api.HttpError
import com.schibsted.account.android.webflows.api.UserTokenResponse
import com.schibsted.account.android.webflows.client.Client
import com.schibsted.account.android.webflows.persistence.SessionStorage
import com.schibsted.account.android.webflows.token.TokenError
import com.schibsted.account.android.webflows.token.TokenHandler
import com.schibsted.account.android.webflows.util.ResultOrError
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import withServer
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
        val httpClient = OkHttpClient.Builder().build()
        val user = User(Fixtures.getClient(okHttpClient = httpClient), Fixtures.userTokens)

        val responseData = "Test data"
        withServer(MockResponse().setResponseCode(200).setBody(responseData)) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertSuccess { assertEquals(responseData, it.body!!.string()) }
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
        val httpClient = OkHttpClient.Builder().build()
        val user = User(Fixtures.getClient(okHttpClient = httpClient), Fixtures.userTokens)

        val request = Request.Builder().url("http://localhost:1").build()

        await { done ->
            user.makeAuthenticatedRequest(request) { result ->
                result.assertError { assertTrue(it is ConnectException) }
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
                ResultOrError.Success(tokensResult)
            }
        }
        val sessionStorageMock: SessionStorage = mockk(relaxed = true) {
            every { save(any()) } returns Unit
        }
        val httpClient = OkHttpClient.Builder().build()
        val user =
            User(
                Fixtures.getClient(
                    okHttpClient = httpClient,
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
                    result.assertSuccess { assertEquals(responseData, it.body!!.string()) }

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
                ResultOrError.Success(tokensResult)
            }
        }
        val httpClient = OkHttpClient.Builder().build()
        val user =
            User(
                Fixtures.getClient(
                    okHttpClient = httpClient,
                    tokenHandler = tokenHandler,
                ),
                Fixtures.userTokens
            )

        val responses = arrayOf(
            MockResponse().setResponseCode(401).setBody("Unauthorized"),
            MockResponse().setResponseCode(401).setBody("Still unauthorized"),
        )
        withServer(*responses) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertSuccess { assertEquals("Still unauthorized", it.body!!.string()) }

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
        val httpClient = OkHttpClient.Builder().build()
        val user =
            User(
                Fixtures.getClient(okHttpClient = httpClient),
                Fixtures.userTokens.copy(refreshToken = null)
            )

        withServer(MockResponse().setResponseCode(401).setBody("Unauthorized")) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertSuccess { assertEquals("Unauthorized", it.body!!.string()) }
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
                ResultOrError.Failure(error)
            }
        }
        val httpClient = OkHttpClient.Builder().build()
        val user =
            User(
                Fixtures.getClient(okHttpClient = httpClient, tokenHandler = tokenHandler),
                Fixtures.userTokens
            )

        withServer(MockResponse().setResponseCode(401).setBody("Unauthorized")) { server ->
            val request = Request.Builder().url(server.url("/")).build()

            await { done ->
                user.makeAuthenticatedRequest(request) { result ->
                    result.assertSuccess { assertEquals("Unauthorized", it.body!!.string()) }
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
            ResultOrError.Success(Fixtures.userTokens.copy(accessToken = "accessToken1"))
        } andThen {
            ResultOrError.Success(Fixtures.userTokens.copy(accessToken = "accessToken2"))
        }

        val t1 = thread {
            val result = user.refreshTokens()
            result.assertSuccess { assertEquals("accessToken1", it.accessToken) }
        }
        val t2 = thread {
            val result = user.refreshTokens()
            result.assertSuccess { assertEquals("accessToken1", it.accessToken) }
        }

        t1.join()
        t2.join()
        verify(exactly = 1) { client.refreshTokensForUser(user) }
    }
}
