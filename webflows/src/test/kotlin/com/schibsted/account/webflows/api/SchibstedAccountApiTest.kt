package com.schibsted.account.webflows.api

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.schibsted.account.testutil.*
import com.schibsted.account.webflows.user.User
import com.schibsted.account.webflows.util.Util
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
            await { done ->
                val result = schaccApi.makeTokenRequest(tokenRequest)
                result.assertRight { assertEquals(tokenResponse, it) }

                val tokenRequestParams =
                    Util.parseQueryParameters(server.takeRequest().body.readUtf8())
                val expectedParams = mapOf(
                    "client_id" to Fixtures.clientConfig.clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to tokenRequest.refreshToken
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
        val jwksResponse = MockResponse().setBody(
            """
            {
                "keys": [$jwk]
            }
            """.trimIndent()
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
                    "published": "1970-01-01 00:00:00",
                    "gender": "withheld",
                    "utcOffset": "+02:00",
                    "addresses": {
                        "home": {
                            "type": "home",
                            "formatted": "12345 Test, Sverige",
                            "streetAddress": "Test",
                            "locality": "Test locality",
                            "region": "Test region",
                            "postalCode": "12345",
                            "country": "Sverige"
                        }
                    },
                    "phoneNumbers": [
                        {
                            "value": "+46123456",
                            "type": "other",
                            "primary": "false",
                            "verified": "false"
                        }
                    ],
                    "phoneNumber": "+46123456",
                    "emailVerified": "1970-01-01 00:00:00",
                    "phoneNumberVerified": false,
                    "lastAuthenticated": "1970-01-01 00:00:00",
                    "lastLoggedIn": "1970-01-01 00:00:00",
                    "verified": "1970-01-01 00:00:00",
                    "uuid": "96085e85-349b-4dbf-9809-fa721e7bae46",
                    "userId": "12345",
                    "displayName": "Unit test",
                    "email": "test@example.com",
                    "birthday": "1970-01-01 00:00:00",
                    "name": {
                        "familyName": "Test",
                        "givenName": "Unit",
                        "formatted": "Unit Test"
                    },
                    "accounts": {
                        "client1": {
                            "id": "client1",
                            "domain": "example.com",
                            "accountName": "Example",
                            "connected": "1970-01-01 00:00:00"
                        }
                    },
                    "merchants": [
                        12345,
                        54321
                    ],
                    "locale": "sv_SE",
                    "emails": [
                        {
                            "value": "test@example.com",
                            "type": "other",
                            "primary": "true",
                            "verified": "true",
                            "verifiedTime": "1970-01-01 00:00:00"
                        }
                    ],
                    "updated": "1971-01-01 00:00:00",
                    "passwordChanged": "1970-01-01 00:00:00",
                    "status": 1
                }
            }
            """.trimIndent()
        )
        withServer(userProfileResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                val user = User(Fixtures.getClient(), Fixtures.userTokens)
                schaccApi.userProfile(user) { result ->
                    result.assertRight {
                        val expectedProfileResponse = UserProfileResponse(
                            uuid = "96085e85-349b-4dbf-9809-fa721e7bae46",
                            userId = "12345",
                            status = 1,
                            email = "test@example.com",
                            emailVerified = "1970-01-01 00:00:00",
                            emails = listOf(
                                Email(
                                    "test@example.com",
                                    "other",
                                    true,
                                    true,
                                    "1970-01-01 00:00:00"
                                )
                            ),
                            phoneNumber = "+46123456",
                            phoneNumberVerified = "false",
                            phoneNumbers = listOf(PhoneNumber("+46123456", "other", false, false)),
                            displayName = "Unit test",
                            name = Name("Unit", "Test", "Unit Test"),
                            addresses = mapOf(
                                Address.AddressType.HOME to Address(
                                    "12345 Test, Sverige",
                                    "Test",
                                    "12345",
                                    "Test locality",
                                    "Test region",
                                    "Sverige",
                                    Address.AddressType.HOME
                                )
                            ),
                            gender = "withheld",
                            birthday = "1970-01-01 00:00:00",
                            accounts = mapOf(
                                "client1" to Account(
                                    "client1",
                                    "Example",
                                    "example.com",
                                    "1970-01-01 00:00:00"
                                )
                            ),
                            merchants = listOf(12345, 54321),
                            published = "1970-01-01 00:00:00",
                            verified = "1970-01-01 00:00:00",
                            updated = "1971-01-01 00:00:00",
                            passwordChanged = "1970-01-01 00:00:00",
                            lastAuthenticated = "1970-01-01 00:00:00",
                            lastLoggedIn = "1970-01-01 00:00:00",
                            locale = "sv_SE",
                            utcOffset = "+02:00"
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
            await { done ->
                val user = User(Fixtures.getClient(), Fixtures.userTokens)
                val clientId = "client1"
                val redirectUri = "https://client1.example.com/redirect"
                schaccApi.sessionExchange(user, clientId, redirectUri) { result ->
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
                    done()
                }
            }
        }
    }
}
