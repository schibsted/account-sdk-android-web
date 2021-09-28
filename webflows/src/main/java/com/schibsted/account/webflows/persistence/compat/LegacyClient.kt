package com.schibsted.account.webflows.persistence.compat

import android.util.Log
import com.schibsted.account.webflows.api.ApiResult
import com.schibsted.account.webflows.api.CodeExchangeResponse
import com.schibsted.account.webflows.api.HttpError
import com.schibsted.account.webflows.api.SchibstedAccountApi
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Logging.SDK_TAG
import okhttp3.Credentials

internal class LegacyClient(
    private val clientId: String,
    private val clientSecret: String,
    private val schibstedAccountApi: SchibstedAccountApi
) {
    fun getAuthCodeFromTokens(
        legacyTokens: UserTokens,
        newClientId: String,
        callback: (ApiResult<CodeExchangeResponse>) -> Unit
    ) {
        schibstedAccountApi.legacyCodeExchange(
            legacyTokens.accessToken,
            newClientId
        ) { result ->
            result
                .map { codeExchangeResponse ->
                    callback(Either.Right(value = codeExchangeResponse))
                }
                .left().map { error ->
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
                                        // token refresh failed, so we return the original 401
                                        callback(result)
                                    }
                                }
                            } else {
                                callback(result)
                            }
                        }
                        else -> callback(result)
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
                .map { userTokenResponse ->
                    Log.d(SDK_TAG, "Refreshed legacy tokens successfully")
                    callback(userTokenResponse.access_token)
                }
                .left().map { err ->
                    Log.e(SDK_TAG, "Failed to refresh legacy tokens: $err")
                    callback(null)
                }
        }
    }
}
