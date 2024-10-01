package com.schibsted.account.webflows.token

import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.RefreshTokenRequest
import com.schibsted.account.webflows.api.SchibstedAccountApi
import com.schibsted.account.webflows.api.UserTokenRequest
import com.schibsted.account.webflows.api.UserTokenResponse
import com.schibsted.account.webflows.client.AuthState
import com.schibsted.account.webflows.client.ClientConfiguration
import com.schibsted.account.webflows.jose.AsyncJwks
import com.schibsted.account.webflows.jose.RemoteJwks
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import timber.log.Timber

internal sealed class TokenError {
    data class TokenRequestError(val cause: HttpError) : TokenError()

    data class IdTokenNotValid(val cause: IdTokenValidationError) : TokenError()

    object NoIdTokenReceived : TokenError()
}

internal data class UserTokensResult(
    val userTokens: UserTokens,
    val scope: String?,
    val expiresIn: Int,
) {
    override fun toString(): String {
        return "UserTokensResult(\n" +
            "userTokens: ${userTokens}\n" +
            "scope: ${scope ?: ""},\n" +
            "expires_in: $expiresIn)"
    }
}

internal typealias TokenRequestResult = Either<TokenError, UserTokensResult>

internal class TokenHandler(
    private val clientConfiguration: ClientConfiguration,
    private val schibstedAccountApi: SchibstedAccountApi,
) {
    private val jwks: AsyncJwks

    init {
        jwks = RemoteJwks(schibstedAccountApi)
    }

    fun makeTokenRequest(
        authCode: String,
        authState: AuthState?,
        callback: (TokenRequestResult) -> Unit,
    ) {
        val tokenRequest =
            UserTokenRequest(
                authCode,
                authState?.codeVerifier,
                clientConfiguration.clientId,
                clientConfiguration.redirectUri,
            )
        schibstedAccountApi.makeTokenRequest(tokenRequest) { result ->
            result
                .onSuccess { handleTokenResponse(it, authState, callback) }
                .onFailure { err ->
                    Timber.d("Token request error response: $err")
                    callback(Left(TokenError.TokenRequestError(err)))
                }
        }
    }

    fun makeTokenRequest(
        refreshToken: String,
        scope: String? = null,
    ): Either<TokenError.TokenRequestError, UserTokenResponse> {
        val tokenRequest =
            RefreshTokenRequest(
                refreshToken,
                scope,
                clientConfiguration.clientId,
            )
        return when (val result = schibstedAccountApi.makeTokenRequest(tokenRequest)) {
            is Right -> result
            is Left -> {
                Timber.d("Token request error response: ${result.value}")
                Left(TokenError.TokenRequestError(result.value))
            }
        }
    }

    private fun handleTokenResponse(
        tokenResponse: UserTokenResponse,
        authState: AuthState?,
        callback: (TokenRequestResult) -> Unit,
    ) {
        val idToken = tokenResponse.id_token
        if (idToken == null) {
            Timber.e("Missing ID Token")
            callback(Left(TokenError.NoIdTokenReceived))
            return
        }

        val idTokenValidationContext =
            IdTokenValidationContext(
                clientConfiguration.issuer,
                clientConfiguration.clientId,
                authState?.nonce,
                authState?.mfa?.value,
            )

        IdTokenValidator.validate(idToken, jwks, idTokenValidationContext) { result ->
            result
                .onSuccess {
                    val tokens =
                        UserTokens(
                            tokenResponse.access_token,
                            tokenResponse.refresh_token,
                            tokenResponse.id_token,
                            it,
                        )
                    callback(
                        Right(
                            UserTokensResult(
                                tokens,
                                tokenResponse.scope,
                                tokenResponse.expires_in,
                            ),
                        ),
                    )
                }
                .onFailure { callback(Left(TokenError.IdTokenNotValid(it))) }
        }
    }
}
