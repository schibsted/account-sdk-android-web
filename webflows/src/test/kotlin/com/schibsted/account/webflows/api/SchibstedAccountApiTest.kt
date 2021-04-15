package com.schibsted.account.webflows.api

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.assertLeft
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.testutil.withServer
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Util
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class SchibstedAccountApiTest {
    private val tokenRequest = UserTokenRequest(
        "authCode",
        "12345",
        "client1",
        "redirectUri"
    )

    private fun assertAuthCodeTokenRequest(
        expectedTokenRequest: UserTokenRequest,
        actualRequest: RecordedRequest
    ) {
        assertEquals("/oauth/token", actualRequest.path)

        val tokenRequestParams = Util.parseQueryParameters(actualRequest.body.readUtf8())
        val expectTokenRequest = mapOf(
            "grant_type" to "authorization_code",
            "code" to expectedTokenRequest.authCode,
            "code_verifier" to expectedTokenRequest.codeVerifier,
            "client_id" to expectedTokenRequest.clientId,
            "redirect_uri" to expectedTokenRequest.redirectUri
        )
        assertEquals(expectTokenRequest, tokenRequestParams)
    }

    @Test
    fun makeAuthCodeTokenRequestSuccess() {
        val tokenResponse = UserTokenResponse(
            "accessToken1",
            "refreshToken1",
            "idToken1",
            "openid offline_access",
            3600
        )
        val httpResponse = MockResponse().setBody(
            """
            {
                "access_token": "${tokenResponse.access_token}",
                "refresh_token": "${tokenResponse.refresh_token}",
                "id_token": "${tokenResponse.id_token}",
                "scope": "${tokenResponse.scope}",
                "expires_in": "${tokenResponse.expires_in}"
            }
            """.trimIndent()
        )

        withServer(httpResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            val result = runBlocking { schaccApi.makeTokenRequest(tokenRequest) }
            result.assertRight { assertEquals(tokenResponse, it) }
            assertAuthCodeTokenRequest(tokenRequest, server.takeRequest())
        }
    }

    @Test
    fun makeRefreshTokenRequestSuccess() {
        val tokenResponse = UserTokenResponse(
            "accessToken1",
            "refreshToken1",
            null,
            null,
            3600
        )
        val httpResponse = MockResponse().setBody(
            """
            {
                "access_token": "${tokenResponse.access_token}",
                "refresh_token": "${tokenResponse.refresh_token}",
                "expires_in": "${tokenResponse.expires_in}"
            }
            """.trimIndent()
        )

        val tokenRequest = RefreshTokenRequest(
            "refreshToken",
            null,
            Fixtures.clientConfig.clientId
        )
        withServer(httpResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            val result = runBlocking { schaccApi.makeTokenRequest(tokenRequest) }
            result.assertRight { assertEquals(tokenResponse, it) }

            val tokenRequestParams =
                Util.parseQueryParameters(server.takeRequest().body.readUtf8())
            val expectedParams = mapOf(
                "client_id" to Fixtures.clientConfig.clientId,
                "grant_type" to "refresh_token",
                "refresh_token" to tokenRequest.refreshToken
            )
            assertEquals(expectedParams, tokenRequestParams)
        }
    }

    @Test
    fun makeTokenRequestConnectionError() {
        val schaccApi =
            SchibstedAccountApi("http://localhost:1".toHttpUrl(), Fixtures.httpClient)
        val result = runBlocking { schaccApi.makeTokenRequest(tokenRequest) }
        result.assertLeft { assertTrue(it is HttpError.UnexpectedError) }
    }

    @Test
    fun makeTokenRequestErrorResponse() {
        val response = MockResponse().setResponseCode(500).setBody("Something went wrong")
        withServer(response) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            val result = runBlocking { schaccApi.makeTokenRequest(tokenRequest) }
            result.assertLeft {
                assertTrue(it is HttpError.ErrorResponse)
                val error = it as HttpError.ErrorResponse
                assertEquals(500, error.code)
                assertEquals("Something went wrong", error.body)
            }
        }
    }

    @Test
    fun jwksSuccess() {
        val jwk = RSAKeyGenerator(2048).generate().toPublicJWK()
        val jwksResponse = MockResponse().setBody(
            """
            {
                "keys": [$jwk]
            }
            """.trimIndent()
        )
        withServer(jwksResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            val result = runBlocking { schaccApi.getJwks() }
            result.assertRight { assertEquals(listOf(jwk), it.keys) }
        }
    }

    @Test
    fun jwksConnectionError() {
        val schaccApi =
            SchibstedAccountApi("http://localhost:1".toHttpUrl(), Fixtures.httpClient)
        val result = runBlocking { schaccApi.getJwks() }
        result.assertLeft { assertTrue(it is HttpError.UnexpectedError) }
    }

    @Test
    fun jwksErrorResponse() {
        val response = MockResponse().setResponseCode(500).setBody("Something went wrong")
        withServer(response) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            val result = runBlocking { schaccApi.getJwks() }
            result.assertLeft {
                assertTrue(it is HttpError.ErrorResponse)
                val error = it as HttpError.ErrorResponse
                assertEquals(500, error.code)
                assertEquals("Something went wrong", error.body)
            }
        }
    }

    @Test
    fun customUserAgentHeaderIsAddedToRequest() {
        val response = MockResponse().setResponseCode(500).setBody("Something went wrong")
        withServer(response) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            runBlocking { schaccApi.getJwks() }
            val userAgentHeader = server.takeRequest().getHeader("User-Agent")
            assertTrue(userAgentHeader!!.contains("AccountSDKAndroidWeb"))
        }
    }

    @Test
    fun userProfileSuccess() {
        val userProfileResponse = MockResponse().setBody(
            """
            {
                "name": "SPP Container",
                "version": "0.2",
                "api": 2,
                "object": "User",
                "type": "element",
                "code": 200,
                "data": {
                    "userId": "12345",
                    "email": "test@example.com",
                    "phoneNumber": "+46123456",
                    "displayName": "Unit test",
                    "name": {
                        "familyName": "Test",
                        "givenName": "Unit",
                        "formatted": "Unit Test"
                    }
                }
            }
            """.trimIndent()
        )
        withServer(userProfileResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            val user = User(Fixtures.getClient(), Fixtures.userTokens)
            val result = runBlocking { schaccApi.userProfile(user) }
            result.assertRight {
                val expectedProfileResponse = UserProfileResponse(
                    "12345",
                    "test@example.com",
                    "+46123456",
                    "Unit test",
                    Name("Unit", "Test", "Unit Test")
                )
                assertEquals(expectedProfileResponse, it)
            }

            // request should contain user access token and custom User-Agent header
            val request = server.takeRequest()
            val authHeader = request.getHeader("Authorization")
            assertEquals("Bearer ${Fixtures.userTokens.accessToken}", authHeader)
            val userAgentHeader = request.getHeader("User-Agent")
            assertTrue(userAgentHeader!!.contains("AccountSDKAndroidWeb"))
        }
    }

    @Test
    fun sessionExchangeSuccess() {
        val sessionExchangeResponse = MockResponse().setBody(
            """
            {
                "name": "SPP Container",
                "version": "0.2",
                "api": 2,
                "object": "User",
                "type": "element",
                "code": 200,
                "data": {
                    "code": "12345"
                }
            }
            """.trimIndent()
        )
        withServer(sessionExchangeResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            val user = User(Fixtures.getClient(), Fixtures.userTokens)
            val clientId = "client1"
            val redirectUri = "https://client1.example.com/redirect"
            val result = runBlocking { schaccApi.sessionExchange(user, clientId, redirectUri) }
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
                    "type" to "session"
                ), requestParams
            )
        }
    }
}
