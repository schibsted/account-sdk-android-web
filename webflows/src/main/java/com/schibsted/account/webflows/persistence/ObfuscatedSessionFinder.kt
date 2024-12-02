package com.schibsted.account.webflows.persistence

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.nimbusds.jwt.JWTParser
import com.schibsted.account.webflows.token.IdTokenClaims
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either
import java.util.Date

/**
 * Crawls through the obfuscated session JSON to find the access token to enable auto log-in.
 */

internal object ObfuscatedSessionFinder {
    private const val USER_TOKENS_KEY = "userTokens"
    private const val REFRESH_TOKEN_KEY = "refreshToken"

    private const val REFRESH_TOKEN_RECOGNIZE_KEY = "sid"
    private const val ID_TOKEN_RECOGNIZE_KEY = "nonce"

    /**
     * Determines if the session is obfuscated.
     *
     * @param storedUserSessionJson the [StoredUserSession] JSON String representation.
     * @return true if the session is obfuscated, false otherwise.
     */
    private fun isSessionObfuscated(storedUserSessionJson: String): Boolean {
        val sessionJsonObject = JsonParser.parseString(storedUserSessionJson).getAsJsonObject()
        if (sessionJsonObject.has(USER_TOKENS_KEY)) {
            val userTokenJsonObject = sessionJsonObject.getAsJsonObject(USER_TOKENS_KEY)
            // If the userTokens key and the refreshToken key is present
            // This means the session is not obfuscated
            if (userTokenJsonObject.has(REFRESH_TOKEN_KEY)) {
                return false
            }
        }
        // Not able to find the userTokens key or the refreshToken key
        // This means the session is obfuscated
        return true
    }

    /**
     * Crawls through the obfuscated [StoredUserSession] JSON to find the Tokens to enable auto log-in.
     *
     * @param clientId the client ID.
     * @param storedUserSessionJson the JSON of the [StoredUserSession] object.
     * @return the [StoredUserSession] if found, null otherwise.
     */
    fun getDeobfuscatedStoredUserSessionIfViable(
        gson: Gson,
        clientId: String,
        storedUserSessionJson: String?,
    ): StorageReadResult {
        try {
            // based on the storedUserSessionJson, determine if the session is obfuscated
            storedUserSessionJson?.let {
                if (isSessionObfuscated(storedUserSessionJson)) {
                    val sessionJsonObject =
                        JsonParser.parseString(storedUserSessionJson).asJsonObject
                    var refreshToken = ""
                    var idToken = ""
                    var accessToken = ""
                    // Iterate through the JSON to find the tokens
                    for (key in sessionJsonObject.keySet()) {
                        val value = sessionJsonObject.get(key)
                        // If the value is a JSON object it means that we're in "userTokens object"
                        // so we iterate through the inner keys to search tokens
                        if (value.isJsonObject) {
                            val jsonObject = value.asJsonObject
                            for (innerKey in jsonObject.keySet()) {
                                val innerValue = jsonObject.get(innerKey)
                                // If the inner value is a JSON primitive, check if it is a token
                                if (innerValue.isJsonPrimitive) {
                                    // We have 1 of 3 tokens: access, refresh or id token
                                    if (innerValue.asString.isNullOrEmpty().not()) {
                                        JWTParser.parse(innerValue.asString).let { jwtValue ->
                                            val payload = jwtValue.jwtClaimsSet.toPayload()
                                            val payloadJson =
                                                JsonParser.parseString(payload.toString()).asJsonObject
                                            // recognize the token based on the payload
                                            // some tokens have specific keys that can be used to recognize them
                                            val refreshTokenRecognized =
                                                payloadJson.has(REFRESH_TOKEN_RECOGNIZE_KEY)
                                            val idTokenRecognized =
                                                payloadJson.has(ID_TOKEN_RECOGNIZE_KEY)
                                            val accessTokenRecognized =
                                                !refreshTokenRecognized && !idTokenRecognized
                                            // if the token is recognized get the token value
                                            if (accessTokenRecognized) {
                                                accessToken = innerValue.asString
                                            } else if (refreshTokenRecognized) {
                                                refreshToken = innerValue.asString
                                            } else {
                                                idToken = innerValue.asString
                                            }
                                        }
                                    }
                                }
                            }
                            continue
                        }
                    }
                    // Create the IdTokenClaims object based on the tokens
                    val idTokenClaims =
                        createIdTokenClaimsBasedOnTokenJsons(
                            refreshToken,
                            accessToken,
                            idToken,
                        )
                    // Create the UserTokens object based on the idTokenClaims and rest of data
                    val userTokens = UserTokens(accessToken, refreshToken, idToken, idTokenClaims)
                    val result = StoredUserSession(clientId, userTokens, Date())
                    return Either.Right(result)
                } else {
                    val result =
                        gson.fromJson(storedUserSessionJson, StoredUserSession::class.java)
                    return Either.Right(result)
                }
            } ?: return Either.Left(StorageError.UnexpectedError(Throwable("No session found.")))
        } catch (e: Exception) {
            return Either.Left(StorageError.UnexpectedError(e))
        }
    }

    /**
     * Creates an [IdTokenClaims] object based on the token JSONs.
     *
     * @param refreshToken the refresh token JSON.
     * @param accessToken the access token JSON.
     * @param idToken the id token JSON.
     * @return the [IdTokenClaims] object.
     */
    fun createIdTokenClaimsBasedOnTokenJsons(
        refreshToken: String?,
        accessToken: String?,
        idToken: String?,
    ): IdTokenClaims {
        val refreshTokenClaims =
            refreshToken.let {
                if (refreshToken.isNullOrEmpty().not()) {
                    JWTParser.parse(refreshToken).jwtClaimsSet
                } else {
                    null
                }
            }
        val accessTokenClaims =
            accessToken.let {
                if (accessToken.isNullOrEmpty().not()) {
                    JWTParser.parse(accessToken).jwtClaimsSet
                } else {
                    null
                }
            }
        val idTokenClaims =
            idToken.let {
                if (idToken.isNullOrEmpty().not()) {
                    JWTParser.parse(idToken).jwtClaimsSet
                } else {
                    null
                }
            }
        return IdTokenClaims(
            idTokenClaims?.getStringClaim("iss") ?: "",
            idTokenClaims?.getStringClaim("sub") ?: "",
            refreshTokenClaims?.getStringClaim("user_id") ?: "",
            idTokenClaims?.getStringListClaim("aud") ?: emptyList(),
            idTokenClaims?.getDateClaim("exp")?.time?.div(1000) ?: 0L,
            idTokenClaims?.getStringClaim("nonce"),
            idTokenClaims?.getStringListClaim("amr"),
        )
    }
}
