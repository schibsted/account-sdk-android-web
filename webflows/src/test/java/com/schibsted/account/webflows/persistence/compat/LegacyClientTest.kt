package com.schibsted.account.webflows.persistence.compat

import android.util.Log
import com.schibsted.account.webflows.api.*
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.util.Either
import io.mockk.*
import org.junit.Before
import org.junit.Test

class LegacyClientTest {

    private lateinit var legacyClient: LegacyClient
    private val clientId = "clientId"
    private val clientSecret = "clientSecret"
    private lateinit var schibstedAccountApi: SchibstedAccountApi
    private val callback = mockk<(ApiResult<CodeExchangeResponse>) -> Unit>()

    @Before
    fun setUpBefore() {
        schibstedAccountApi = mockk()
        legacyClient = LegacyClient(clientId, clientSecret, schibstedAccountApi)

        every { callback(any()) } just Runs

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

    }

    @Test
    fun `getAuthCodeFromTokens should callback with code if initial legacyCodeExchange is successful`() {
        val legacyToken = UserTokens(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            idToken = "idToken",
            idTokenClaims = mockk()
        )
        val response = Either.Right(CodeExchangeResponse("code"))

        every {
            schibstedAccountApi.legacyCodeExchange(
                "accessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(response)
        }

        legacyClient.getAuthCodeFromTokens(
            legacyTokens = legacyToken,
            newClientId = "newClientId",
            callback = callback
        )

        verify(exactly = 1) { callback(response) }
    }

    @Test
    fun `getAuthCodeFromTokens should forward error through callback if errorcode is not 401`() {
        val legacyToken = UserTokens(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            idToken = "idToken",
            idTokenClaims = mockk()
        )
        val errorResponse = Either.Left(HttpError.ErrorResponse(300, "body"))

        every {
            schibstedAccountApi.legacyCodeExchange(
                "accessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(errorResponse)
        }

        legacyClient.getAuthCodeFromTokens(
            legacyTokens = legacyToken,
            newClientId = "newClientId",
            callback = callback
        )

        verify(exactly = 1) { callback(errorResponse) }
    }

    @Test
    fun `getAuthCodeFromTokens should forward error through callback if error code is 401, but missing refreshToken`() {
        val legacyToken = UserTokens(
            accessToken = "accessToken",
            refreshToken = null,
            idToken = "idToken",
            idTokenClaims = mockk()
        )
        val errorResponse = Either.Left(HttpError.ErrorResponse(401, "body"))

        every {
            schibstedAccountApi.legacyCodeExchange(
                "accessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(errorResponse)
        }

        legacyClient.getAuthCodeFromTokens(
            legacyTokens = legacyToken,
            newClientId = "newClientId",
            callback = callback
        )

        verify(exactly = 1) { callback(errorResponse) }
    }

    @Test
    fun `getAuthCodeFromTokens should forward error through callback if error is other than ErrorResponse`() {
        val legacyToken = UserTokens(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            idToken = "idToken",
            idTokenClaims = mockk()
        )
        val errorResponse =
            Either.Left(HttpError.UnexpectedError(Throwable("something went wrong")))

        every {
            schibstedAccountApi.legacyCodeExchange(
                "accessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(errorResponse)
        }

        legacyClient.getAuthCodeFromTokens(
            legacyTokens = legacyToken,
            newClientId = "newClientId",
            callback = callback
        )

        verify(exactly = 1) { callback(errorResponse) }
    }

    @Test
    fun `getAuthCodeFromTokens should forward error through callback if error is 401 and refreshToken is available with refreshToken, but refreshing refreshToken returns null`() {
        val legacyToken = UserTokens(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            idToken = "idToken",
            idTokenClaims = mockk()
        )
        val errorResponse = Either.Left(HttpError.ErrorResponse(401, "body"))
        val userTokenErrorResponse = Either.Left(HttpError.ErrorResponse(401, "body"))

        every {
            schibstedAccountApi.legacyCodeExchange(
                "accessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(errorResponse)
        }
        every {
            schibstedAccountApi.legacyRefreshTokenRequest(
                any(),
                any(),
                any()
            )
        } answers {
            val userTokenResponceCallback = thirdArg<(ApiResult<UserTokenResponse>) -> Unit>()
            userTokenResponceCallback(userTokenErrorResponse)
        }

        legacyClient.getAuthCodeFromTokens(
            legacyTokens = legacyToken,
            newClientId = "newClientId",
            callback = callback
        )

        verify(exactly = 1) { callback(errorResponse) }
    }

    @Test
    fun `getAuthCodeFromTokens should callback with code if error is 401 and refreshToken is available with refreshToken, and refreshing refreshToken is sucessful`() {
        val legacyToken = UserTokens(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            idToken = "idToken",
            idTokenClaims = mockk()
        )
        val errorResponse = Either.Left(HttpError.ErrorResponse(401, "body"))
        val userTokenResponse =
            Either.Right(
                UserTokenResponse(
                    "freshAccessToken",
                    "freshRefreshToken",
                    "freshIdToken",
                    "scope",
                    60
                )
            )

        every {
            schibstedAccountApi.legacyCodeExchange(
                "accessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(errorResponse)
        }
        every {
            schibstedAccountApi.legacyRefreshTokenRequest(
                any(),
                any(),
                any()
            )
        } answers {
            val refreshTokenCallback = thirdArg<(ApiResult<UserTokenResponse>) -> Unit>()
            refreshTokenCallback(userTokenResponse)
        }
        every {
            schibstedAccountApi.legacyCodeExchange(
                "freshAccessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(errorResponse)
        }

        legacyClient.getAuthCodeFromTokens(
            legacyTokens = legacyToken,
            newClientId = "newClientId",
            callback = callback
        )

        verify(exactly = 1) { callback(errorResponse) }
    }

    @Test
    fun `getAuthCodeFromTokens should callback with code if error is 401 and refreshToken is available with refreshToken, and refreshing refreshToken is sucessful and legacyCodeExchange is successful`() {
        val legacyToken = UserTokens(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            idToken = "idToken",
            idTokenClaims = mockk()
        )
        val errorResponse = Either.Left(HttpError.ErrorResponse(401, "body"))
        val userTokenResponse =
            Either.Right(UserTokenResponse("freshAccessToken", "freshRefreshToken", "freshIdToken", "scope", 60))
        val response = Either.Right(CodeExchangeResponse("code"))

        every {
            schibstedAccountApi.legacyCodeExchange(
                "accessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(errorResponse)
        }
        every {
            schibstedAccountApi.legacyRefreshTokenRequest(
                any(),
                any(),
                any()
            )
        } answers {
            val refreshTokenCallback = thirdArg<(ApiResult<UserTokenResponse>) -> Unit>()
            refreshTokenCallback(userTokenResponse)
        }
        every {
            schibstedAccountApi.legacyCodeExchange(
                "freshAccessToken",
                "newClientId",
                any()
            )
        } answers {
            val legacyCodeExchangeCallback = thirdArg<(ApiResult<CodeExchangeResponse>) -> Unit>()
            legacyCodeExchangeCallback(response)
        }

        legacyClient.getAuthCodeFromTokens(
            legacyTokens = legacyToken,
            newClientId = "newClientId",
            callback = callback
        )

        verify(exactly = 1) { callback(response) }
    }
}
