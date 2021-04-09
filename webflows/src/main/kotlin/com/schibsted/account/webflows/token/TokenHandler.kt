package com.schibsted.account.webflows.token

import android.util.Log
import com.schibsted.account.webflows.Logging
import com.schibsted.account.webflows.api.*
import com.schibsted.account.webflows.client.AuthState
import com.schibsted.account.webflows.client.ClientConfiguration
import com.schibsted.account.webflows.jose.AsyncJwks
import com.schibsted.account.webflows.jose.RemoteJwks
import com.schibsted.account.webflows.token.TokenError.*
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right

internal sealed class TokenError {
    data class TokenRequestError(val cause: HttpError) : TokenError()
    data class IdTokenNotValid(val cause: IdTokenValidationError) : TokenError()
    object NoIdTokenReceived : TokenError()
}

internal data class UserTokensResult(
    val userTokens: UserTokens,
    val scope: String?,
    val expiresIn: Int
) {
    override fun toString(): String {
        return "UserTokensResult(\n" +
                "userTokens: ${userTokens}\n" +
                "scope: ${scope ?: ""},\n" +
                "expires_in: ${expiresIn})"
    }
}

internal typealias TokenRequestResult = Either<TokenError, UserTokensResult>

internal class TokenHandler(
    private val clientConfiguration: ClientConfiguration,
    private val schibstedAccountApi: SchibstedAccountApi
) {
    private val jwks: AsyncJwks

    init {
        jwks = RemoteJwks(schibstedAccountApi)
    }

    fun makeTokenRequest(
        authCode: String,
        authState: AuthState,
        callback: (TokenRequestResult) -> Unit
    ) {
        val tokenRequest = UserTokenRequest(
            authCode,
            authState.codeVerifier,
            clientConfiguration.clientId,
            clientConfiguration.redirectUri
        )
        schibstedAccountApi.makeTokenRequest(tokenRequest) { result ->
            result
                .foreach { handleTokenResponse(it, authState, callback) }
                .left().foreach { err ->
                    Log.d(Logging.SDK_TAG, "Token request error response: $err")
                    callback(Left(TokenRequestError(err)))
                }
        }
    }

    fun makeTokenRequest(
        refreshToken: String,
        scope: String? = null
    ): Either<TokenRequestError, UserTokenResponse> {
        val tokenRequest = RefreshTokenRequest(
            refreshToken,
            scope,
            clientConfiguration.clientId
        )
        val result = schibstedAccountApi.makeTokenRequest(tokenRequest)
        return when (result) {
            is Right -> result
            is Left -> {
                Log.d(Logging.SDK_TAG, "Token request error response: ${result.value}")
                Left(TokenRequestError(result.value))
            }
        }
    }

    private fun handleTokenResponse(
        tokenResponse: UserTokenResponse,
        authState: AuthState,
        callback: (TokenRequestResult) -> Unit
    ) {
        Log.d(Logging.SDK_TAG, "Token response: $tokenResponse")

        val idToken = tokenResponse.id_token
        if (idToken == null) {
            Log.e(Logging.SDK_TAG, "Missing ID Token")
            callback(Left(NoIdTokenReceived))
            return
        }

        val idTokenValidationContext = IdTokenValidationContext(
            clientConfiguration.issuer,
            clientConfiguration.clientId,
            authState.nonce,
            authState.mfa?.value
        )

        IdTokenValidator.validate(idToken, jwks, idTokenValidationContext) { result ->
            result
                .foreach {
                    val tokens = UserTokens(
                        tokenResponse.access_token,
                        tokenResponse.refresh_token,
                        tokenResponse.id_token,
                        it
                    )
                    callback(
                        Right(
                            UserTokensResult(
                                tokens,
                                tokenResponse.scope,
                                tokenResponse.expires_in
                            )
                        )
                    )
                }
                .left().foreach { callback(Left(IdTokenNotValid(it))) }
        }
    }
}
