package com.schibsted.account.webflows.persistence

import android.util.Log
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
        val sessionJsonObject = JsonParser().parse(storedUserSessionJson).getAsJsonObject()
        if (sessionJsonObject.has(USER_TOKENS_KEY)) {
            Log.d("###", "isSessionObfuscated: session HAS $USER_TOKENS_KEY")
            println("isSessionObfuscated: session HAS $USER_TOKENS_KEY")
            val userTokenJsonObject = sessionJsonObject.getAsJsonObject(USER_TOKENS_KEY)
            // If the userTokens key and the refreshToken key is present
            // This means the session is not obfuscated
            if (userTokenJsonObject.has(REFRESH_TOKEN_KEY)) {
                Log.d("###", "isSessionObfuscated: session HAS $REFRESH_TOKEN_KEY")
                println("isSessionObfuscated: session HAS $REFRESH_TOKEN_KEY")
                Log.d("###", "isSessionObfuscated: session IS NOT obfuscated")
                println("isSessionObfuscated: session IS NOT obfuscated")
                return false
            }
        }
        // Not able to find the userTokens key or the refreshToken key
        // This means the session is obfuscated
        Log.d("###", "isSessionObfuscated: session IS obfuscated")
        println("isSessionObfuscated: session IS obfuscated")
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
        storedUserSessionJson: String?
    ): StorageReadResult {
        try {
            Log.d("###", "getDeobfuscatedStoredUserSessionIfViable: $storedUserSessionJson")
            println("getDeobfuscatedStoredUserSessionIfViable: $storedUserSessionJson")

            storedUserSessionJson?.let {
                if (isSessionObfuscated(storedUserSessionJson)) {
                    val sessionJsonObject =
                        JsonParser().parse(storedUserSessionJson).asJsonObject
                    var refreshToken = ""
                    var idToken = ""
                    var accessToken = ""
                    for (key in sessionJsonObject.keySet()) {
                        val value = sessionJsonObject.get(key)
                        if (value.isJsonObject) {
                            val jsonObject = value.asJsonObject
                            for (innerKey in jsonObject.keySet()) {
                                val innerValue = jsonObject.get(innerKey)
                                if (innerValue.isJsonPrimitive) {
                                    if (innerValue.asString.isNullOrEmpty().not()) {
                                        JWTParser.parse(innerValue.asString).let { jwtValue ->
                                            val payload = jwtValue.jwtClaimsSet.toPayload()
                                            val payloadJson =
                                                JsonParser().parse(payload.toString()).asJsonObject
                                            val refreshTokenRecognized =
                                                payloadJson.has(REFRESH_TOKEN_RECOGNIZE_KEY)
                                            val idTokenRecognized =
                                                payloadJson.has(ID_TOKEN_RECOGNIZE_KEY)
                                            val accessTokenRecognized =
                                                !refreshTokenRecognized && !idTokenRecognized
                                            if (accessTokenRecognized) {
                                                Log.d("###", "Found Access token $payload")
                                                println("Found Access token $payload")
                                                println("Found Access token $innerValue")
                                                accessToken = innerValue.asString
                                            } else if (refreshTokenRecognized) {
                                                Log.d("###", "Found Refresh token $payload")
                                                println("Found Refresh token $payload")
                                                println("Found Refresh token $innerValue")
                                                refreshToken = innerValue.asString
                                            } else if (idTokenRecognized) {
                                                Log.d("###", "Found IdToken token $payload")
                                                println("Found IdToken token $payload")
                                                println("Found IdToken token $innerValue")
                                                idToken = innerValue.asString
                                            }
                                        }
                                    }
                                }
                            }
                            continue
                        }
                    }
                    val idTokenClaims = createIdTokenClaimsBasedOnTokenJsons(
                        refreshToken,
                        accessToken,
                        idToken
                    )
                    val userTokens = UserTokens(accessToken, refreshToken, idToken, idTokenClaims)
                    val result = StoredUserSession(clientId, userTokens, Date())
                    Log.d(
                        "###",
                        "Returning StoredUserSession with clientID: $clientId and userTokens: $result"
                    )
                    return Either.Right(result)
                } else {
                    val result =
                        gson.fromJson(storedUserSessionJson, StoredUserSession::class.java)
                    Log.d("###", "Session was not obfuscated returning pure data: $result")
                    println("Session was not obfuscated returning pure data: $result")
                    return Either.Right(result)
                }
            }
        } catch (e: Exception) {
            Log.d("###", "Session was obfuscated but encountered exception $e")
            println("Session not obfuscated but encountered exception $e")
            return Either.Left(StorageError.UnexpectedError(e))
        }
        return Either.Left(StorageError.UnexpectedError(Throwable("Unknown error.")))
    }

    fun createIdTokenClaimsBasedOnTokenJsons(
        refreshToken: String?,
        accessToken: String?,
        idToken: String?
    ): IdTokenClaims {
        val refreshTokenClaims =
            refreshToken.let {
                if (refreshToken.isNullOrEmpty().not()) {
                    JWTParser.parse(refreshToken).jwtClaimsSet
                } else null
            }
        val accessTokenClaims =
            accessToken.let {
                if (accessToken.isNullOrEmpty().not()) {
                    JWTParser.parse(accessToken).jwtClaimsSet
                } else null
            }
        val idTokenClaims = idToken.let {
            if (idToken.isNullOrEmpty().not()) {
                JWTParser.parse(idToken).jwtClaimsSet
            } else null
        }
        return IdTokenClaims(
            idTokenClaims?.getStringClaim("iss") ?: "",
            idTokenClaims?.getStringClaim("sub") ?: "",
            refreshTokenClaims?.getStringClaim("user_id") ?: "",
            idTokenClaims?.getStringListClaim("aud") ?: emptyList(),
            idTokenClaims?.getDateClaim("exp")?.time?.div(1000) ?: 0L,
            idTokenClaims?.getStringClaim("nonce"),
            idTokenClaims?.getStringListClaim("amr")
        )
    }
}