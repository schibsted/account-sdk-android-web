package com.schibsted.account.webflows.api

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.assertLeft
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.testutil.await
import com.schibsted.account.testutil.withServer
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Util
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchibstedAccountApiTest {
    private val tokenRequest =
        UserTokenRequest(
            "authCode",
            "12345",
            "client1",
            "redirectUri",
        )

    private fun assertAuthCodeTokenRequest(
        expectedTokenRequest: UserTokenRequest,
        actualRequest: RecordedRequest,
    ) {
        assertEquals("/oauth/token", actualRequest.path)

        val tokenRequestParams = Util.parseQueryParameters(actualRequest.body.readUtf8())
        val expectTokenRequest =
            mapOf(
                "grant_type" to "authorization_code",
                "code" to expectedTokenRequest.authCode,
                "code_verifier" to expectedTokenRequest.codeVerifier,
                "client_id" to expectedTokenRequest.clientId,
                "redirect_uri" to expectedTokenRequest.redirectUri,
            )
        assertEquals(expectTokenRequest, tokenRequestParams)
    }

    private fun withApiResponseWrapper(responseData: String): String {
        return """
            {
                "name": "SPP Container",
                "version": "0.2",
                "api": 2,
                "code": 200,
                "data": $responseData
            }
            """.trimIndent()
    }

    @Test
    fun makeAuthCodeTokenRequestSuccess() {
        val tokenResponse =
            UserTokenResponse(
                "accessToken1",
                "refreshToken1",
                "idToken1",
                "openid offline_access",
                3600,
            )
        val httpResponse =
            MockResponse().setBody(
                """
                {
                    "access_token": "${tokenResponse.access_token}",
                    "refresh_token": "${tokenResponse.refresh_token}",
                    "id_token": "${tokenResponse.id_token}",
                    "scope": "${tokenResponse.scope}",
                    "expires_in": "${tokenResponse.expires_in}"
                }
                """.trimIndent(),
            )

        withServer(httpResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                schaccApi.makeTokenRequest(tokenRequest) { result ->
                    result.assertRight { assertEquals(tokenResponse, it) }
                    assertAuthCodeTokenRequest(tokenRequest, server.takeRequest())
                    done()
                }
            }
        }
    }

    @Test
    fun makeRefreshTokenRequestSuccess() {
        val tokenResponse =
            UserTokenResponse(
                "accessToken1",
                "refreshToken1",
                null,
                null,
                3600,
            )
        val httpResponse =
            MockResponse().setBody(
                """
                {
                    "access_token": "${tokenResponse.access_token}",
                    "refresh_token": "${tokenResponse.refresh_token}",
                    "expires_in": "${tokenResponse.expires_in}"
                }
                """.trimIndent(),
            )

        val tokenRequest =
            RefreshTokenRequest(
                "refreshToken",
                null,
                Fixtures.clientConfig.clientId,
            )
        withServer(httpResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                val result = schaccApi.makeTokenRequest(tokenRequest)
                result.assertRight { assertEquals(tokenResponse, it) }

                val tokenRequestParams =
                    Util.parseQueryParameters(server.takeRequest().body.readUtf8())
                val expectedParams =
                    mapOf(
                        "client_id" to Fixtures.clientConfig.clientId,
                        "grant_type" to "refresh_token",
                        "refresh_token" to tokenRequest.refreshToken,
                    )
                assertEquals(expectedParams, tokenRequestParams)
                done()
            }
        }
    }

    @Test
    fun makeTokenRequestConnectionError() {
        val schaccApi =
            SchibstedAccountApi("http://localhost:1".toHttpUrl(), Fixtures.httpClient)
        await { done ->
            schaccApi.makeTokenRequest(tokenRequest) { result ->
                result.assertLeft { assertTrue(it is HttpError.UnexpectedError) }
                done()
            }
        }
    }

    @Test
    fun makeTokenRequestErrorResponse() {
        val response = MockResponse().setResponseCode(500).setBody("Something went wrong")
        withServer(response) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                schaccApi.makeTokenRequest(tokenRequest) { result ->
                    result.assertLeft {
                        assertTrue(it is HttpError.ErrorResponse)
                        val error = it as HttpError.ErrorResponse
                        assertEquals(500, error.code)
                        assertEquals("Something went wrong", error.body)
                    }
                    done()
                }
            }
        }
    }

    @Test
    fun jwksSuccess() {
        val jwk = RSAKeyGenerator(2048).generate().toPublicJWK()
        val jwksResponse =
            MockResponse().setBody(
                """
                {
                    "keys": [$jwk]
                }
                """.trimIndent(),
            )
        withServer(jwksResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                schaccApi.getJwks { result ->
                    result.assertRight { assertEquals(listOf(jwk), it.keys) }
                    done()
                }
            }
        }
    }

    @Test
    fun jwksConnectionError() {
        val schaccApi =
            SchibstedAccountApi("http://localhost:1".toHttpUrl(), Fixtures.httpClient)
        await { done ->
            schaccApi.getJwks { result ->
                result.assertLeft { assertTrue(it is HttpError.UnexpectedError) }
                done()
            }
        }
    }

    @Test
    fun jwksErrorResponse() {
        val response = MockResponse().setResponseCode(500).setBody("Something went wrong")
        withServer(response) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                schaccApi.getJwks { result ->
                    result.assertLeft {
                        assertTrue(it is HttpError.ErrorResponse)
                        val error = it as HttpError.ErrorResponse
                        assertEquals(500, error.code)
                        assertEquals("Something went wrong", error.body)
                    }
                    done()
                }
            }
        }
    }

    @Test
    fun customUserAgentHeaderIsAddedToRequest() {
        val response = MockResponse().setResponseCode(500).setBody("Something went wrong")
        withServer(response) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                schaccApi.getJwks { result ->
                    val userAgentHeader = server.takeRequest().getHeader("User-Agent")
                    assertTrue(userAgentHeader!!.contains("AccountSDKAndroidWeb"))
                    done()
                }
            }
        }
    }

    @Test
    fun userProfileSuccess() {
        val userProfileResponse =
            MockResponse().setBody(
                withApiResponseWrapper(
                    """
                    {
                        "uuid": "96085e85-349b-4dbf-9809-fa721e7bae46"
                    }
                    """.trimIndent(),
                ),
            )
        withServer(userProfileResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                val user = User(Fixtures.getClient(), Fixtures.userTokens)
                schaccApi.userProfile(user) { result ->
                    result.assertRight {
                        val expectedProfileResponse =
                            UserProfileResponse(
                                uuid = "96085e85-349b-4dbf-9809-fa721e7bae46",
                            )
                        assertEquals(expectedProfileResponse, it)
                    }

                    // request should contain user access token and custom User-Agent header
                    val request = server.takeRequest()
                    val authHeader = request.getHeader("Authorization")
                    assertEquals("Bearer ${Fixtures.userTokens.accessToken}", authHeader)
                    val userAgentHeader = request.getHeader("User-Agent")
                    assertTrue(userAgentHeader!!.contains("AccountSDKAndroidWeb"))
                    done()
                }
            }
        }
    }

    @Test
    fun sessionExchangeSuccess() {
        val sessionExchangeResponse =
            MockResponse().setBody(
                withApiResponseWrapper(
                    """{"code": "12345"}""",
                ),
            )
        withServer(sessionExchangeResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                val user = User(Fixtures.getClient(), Fixtures.userTokens)
                val clientId = "client1"
                val state = "state1"
                val redirectUri = "https://client1.example.com/redirect"
                schaccApi.sessionExchange(
                    user = user,
                    state = state,
                    clientId = clientId,
                    redirectUri = redirectUri,
                ) { result ->
                    result.assertRight {
                        val expectedSessionExchangeResponse = SessionExchangeResponse("12345")
                        assertEquals(expectedSessionExchangeResponse, it)
                    }

                    val request = server.takeRequest()
                    // request should contain user access token
                    val authHeader = request.getHeader("Authorization")
                    assertEquals("Bearer ${Fixtures.userTokens.accessToken}", authHeader)

                    val requestParams = Util.parseQueryParameters(request.body.readUtf8())
                    assertEquals(
                        mapOf(
                            "clientId" to clientId,
                            "redirectUri" to redirectUri,
                            "type" to "session",
                            "state" to state,
                        ),
                        requestParams,
                    )
                    done()
                }
            }
        }
    }
}
