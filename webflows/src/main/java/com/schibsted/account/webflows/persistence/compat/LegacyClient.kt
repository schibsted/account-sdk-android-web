package com.schibsted.account.webflows.persistence.compat

import com.schibsted.account.webflows.api.ApiResult
import com.schibsted.account.webflows.api.CodeExchangeResponse
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.SchibstedAccountApi
import com.schibsted.account.webflows.token.MigrationUserTokens
import com.schibsted.account.webflows.util.Either
import okhttp3.Credentials
import timber.log.Timber

internal class LegacyClient(
    private val clientId: String,
    private val clientSecret: String,
    private val schibstedAccountApi: SchibstedAccountApi
) {
    fun getAuthCodeFromTokens(
        legacyTokens: MigrationUserTokens,
        newClientId: String,
        callback: (ApiResult<CodeExchangeResponse>) -> Unit
    ) {
        schibstedAccountApi.legacyCodeExchange(
            legacyTokens.accessToken,
            newClientId
        ) { result ->
            result
                .onSuccess { codeExchangeResponse ->
                    callback(Either.Right(value = codeExchangeResponse))
                }
                .onFailure { error ->
                    when (error) {
                        is HttpError.ErrorResponse -> {
                            if (error.code == 401 && legacyTokens.refreshToken != null) {
                                refreshRequest(legacyTokens.refreshToken) { freshAccessToken ->
                                    if (freshAccessToken != null) {
                                        // token refresh the code exchange with fresh token
                                        schibstedAccountApi.legacyCodeExchange(
                                            freshAccessToken,
                                            newClientId,
                                            callback
                                        )
                                    } else {
                                        Timber.d("Token refresh failed, return the original 401")
                                        callback(result)
                                    }
                                }
                            } else {
                                Timber.d("LegacyCodeExchange failed with error: $error")
                                callback(result)
                            }
                        }
                        else -> {
                            Timber.d("LegacyCodeExchange failed with error: $error")
                            callback(result)
                        }
                    }
                }
        }
    }

    private fun refreshRequest(refreshToken: String, callback: (String?) -> Unit) {
        schibstedAccountApi.legacyRefreshTokenRequest(
            Credentials.basic(clientId, clientSecret),
            refreshToken
        ) { userTokenApiResponse ->
            userTokenApiResponse
                .onSuccess { userTokenResponse ->
                    Timber.d("Refreshed legacy tokens successfully")
                    callback(userTokenResponse.access_token)
                }
                .onFailure { err ->
                    Timber.e("Failed to refresh legacy tokens: $err")
                    callback(null)
                }
        }
    }
}
