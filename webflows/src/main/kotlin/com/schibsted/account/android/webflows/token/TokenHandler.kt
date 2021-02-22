package com.schibsted.account.android.webflows.token

import android.util.Log
import com.schibsted.account.android.webflows.AuthState
import com.schibsted.account.android.webflows.Logging
import com.schibsted.account.android.webflows.api.HttpError
import com.schibsted.account.android.webflows.api.SchibstedAccountAPI
import com.schibsted.account.android.webflows.api.UserTokenRequest
import com.schibsted.account.android.webflows.api.UserTokenResponse
import com.schibsted.account.android.webflows.client.ClientConfiguration
import com.schibsted.account.android.webflows.jose.AsyncJwks
import com.schibsted.account.android.webflows.jose.RemoteJwks
import com.schibsted.account.android.webflows.token.TokenError.*
import com.schibsted.account.android.webflows.util.ResultOrError
import com.schibsted.account.android.webflows.util.ResultOrError.Failure
import com.schibsted.account.android.webflows.util.ResultOrError.Success

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


internal class TokenHandler(
    private val clientConfiguration: ClientConfiguration,
    private val schibstedAccountApi: SchibstedAccountAPI
) {
    private val jwks: AsyncJwks

    init {
        jwks = RemoteJwks(schibstedAccountApi)
    }

    fun makeTokenRequest(
        authCode: String,
        authState: AuthState,
        callback: (ResultOrError<UserTokensResult, TokenError>) -> Unit
    ) {
        val tokenRequest = UserTokenRequest(
            authCode,
            authState.codeVerifier,
            clientConfiguration.clientId,
            clientConfiguration.redirectUri
        )
        schibstedAccountApi.makeTokenRequest(tokenRequest) { result ->
            result
                .onSuccess { handleTokenResponse(it, authState, callback) }
                .onFailure { err ->
                    Log.d(Logging.SDK_TAG, "Token request error response: $err")
                    callback(Failure(TokenRequestError(err)))
                }
        }
    }

    private fun handleTokenResponse(
        tokenResponse: UserTokenResponse,
        authState: AuthState,
        callback: (ResultOrError<UserTokensResult, TokenError>) -> Unit
    ) {
        Log.d(Logging.SDK_TAG, "Token response: $tokenResponse")

        val idToken = tokenResponse.id_token
        if (idToken == null) {
            Log.e(Logging.SDK_TAG, "Missing ID Token")
            callback(Failure(NoIdTokenReceived))
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
                .onSuccess {
                    val tokens = UserTokens(
                        tokenResponse.access_token,
                        tokenResponse.refresh_token,
                        tokenResponse.id_token,
                        it
                    )
                    callback(
                        Success(
                            UserTokensResult(
                                tokens,
                                tokenResponse.scope,
                                tokenResponse.expires_in
                            )
                        )
                    )
                }
                .onFailure { callback(Failure(IdTokenNotValid(it))) }
        }
    }
}
