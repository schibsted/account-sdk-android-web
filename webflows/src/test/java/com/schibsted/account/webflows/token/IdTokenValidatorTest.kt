package com.schibsted.account.webflows.token

import com.nimbusds.jose.Payload
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.schibsted.account.testutil.assertLeft
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.testutil.createJws
import com.schibsted.account.webflows.jose.AsyncJwks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

private class TestJwks(private val jwks: JWKSet?) : AsyncJwks {
    override fun fetch(callback: (JWKSet?) -> Unit) {
        callback(jwks)
    }
}

class IdTokenValidatorTest {
    private val jwk: JWK
    private val jwks: JWKSet

    init {
        jwk =
            RSAKeyGenerator(2048)
                .keyID(ID_TOKEN_KEY_ID)
                .generate()
        jwks = JWKSet(jwk)
    }

    private fun defaultIdTokenClaims(): JWTClaimsSet.Builder {
        return JWTClaimsSet.Builder()
            .issuer(ISSUER)
            .audience(CLIENT_ID)
            .expirationTime(Date(System.currentTimeMillis() + 5000))
            .subject(USER_UUID)
            .claim("legacy_user_id", USER_ID)
            .claim("nonce", NONCE)
    }

    private fun createIdToken(claims: JWTClaimsSet): String {
        return createJws(jwk.toRSAKey(), ID_TOKEN_KEY_ID, Payload(claims.toJSONObject()))
    }

    private fun idTokenClaims(claims: JWTClaimsSet): IdTokenClaims {
        return IdTokenClaims(
            claims.issuer,
            claims.subject,
            claims.getStringClaim("legacy_user_id"),
            claims.audience,
            (claims.expirationTime.time / 1000),
            claims.getStringClaim("nonce"),
            claims.getStringListClaim("amr"),
        )
    }

    private fun assertErrorMessage(
        expectedSubstring: String,
        actualMessage: String,
    ) {
        assertTrue(
            "'$expectedSubstring' not in '$actualMessage'",
            actualMessage.contains(expectedSubstring),
        )
    }

    @Test
    fun testAcceptsValidWithoutAMR() {
        val context = IdTokenValidationContext(ISSUER, CLIENT_ID, NONCE, null)
        val claims = defaultIdTokenClaims().build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertRight { assertEquals(idTokenClaims(claims), it) }
        }
    }

    @Test
    fun testAcceptsValidWithExpectedAMR() {
        val expectedAmrValue = "testValue"
        val context = IdTokenValidationContext(ISSUER, CLIENT_ID, NONCE, expectedAmrValue)
        val claims =
            defaultIdTokenClaims()
                .claim("amr", listOf(expectedAmrValue, "otherValue"))
                .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertRight { assertEquals(idTokenClaims(claims), it) }
        }
    }

    @Test
    fun testAcceptsEidAMRWithoutCountryPrefix() {
        val expectedAmrValue = "eid-se"
        val context = IdTokenValidationContext(ISSUER, CLIENT_ID, NONCE, expectedAmrValue)
        val claims =
            defaultIdTokenClaims()
                .claim("amr", listOf("eid", "otherValue"))
                .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertRight { assertEquals(idTokenClaims(claims), it) }
        }
    }

    @Test
    fun testRejectMissingExpectedAMRInIdTokenWithoutAMR() {
        val context = IdTokenValidationContext(ISSUER, CLIENT_ID, NONCE, "testValue")

        for (amr in listOf(null, emptyList(), listOf("otherValue1", "otherValue2"))) {
            val claims =
                defaultIdTokenClaims()
                    .claim("amr", amr)
                    .build()
            val idToken = createIdToken(claims)
            IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
                result.assertLeft { assertErrorMessage("Missing expected AMR value", it.message) }
            }
        }
    }

    @Test
    fun testRejectsMismatchingNonce() {
        val context = IdTokenValidationContext(ISSUER, CLIENT_ID, NONCE, null)

        for (nonce in listOf(null, "otherNonce")) {
            val claims =
                defaultIdTokenClaims()
                    .claim("nonce", nonce)
                    .build()
            val idToken = createIdToken(claims)
            IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
                result.assertLeft { assertErrorMessage("nonce", it.message) }
            }
        }
    }

    @Test
    fun testRejectsMismatchingIssuer() {
        val context = IdTokenValidationContext(ISSUER, CLIENT_ID, NONCE, null)

        val claims =
            defaultIdTokenClaims()
                .issuer("https://other.example.com")
                .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertLeft { assertErrorMessage("Invalid issuer", it.message) }
        }
    }

    @Test
    fun testAcceptsIssuerWithTrailingSlash() {
        val issuerWithSlash = "$ISSUER/"
        val testData =
            listOf(
                ISSUER to ISSUER,
                ISSUER to issuerWithSlash,
                issuerWithSlash to ISSUER,
                issuerWithSlash to issuerWithSlash,
            )

        for ((idTokenIssuer, expectedIssuer) in testData) {
            val context = IdTokenValidationContext(expectedIssuer, CLIENT_ID, NONCE, null)
            val claims =
                defaultIdTokenClaims()
                    .issuer(idTokenIssuer)
                    .build()
            val idToken = createIdToken(claims)
            IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
                result.assertRight { assertEquals(idTokenClaims(claims), it) }
            }
        }
    }

    @Test
    fun testRejectsAudienceClaimWithoutExpectedClientId() {
        val context = IdTokenValidationContext(ISSUER, CLIENT_ID, NONCE, null)

        val claims =
            defaultIdTokenClaims()
                .audience("otherClient")
                .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertLeft { assertErrorMessage("audience", it.message) }
        }
    }

    @Test
    fun testRejectsExpiredIdToken() {
        val context = IdTokenValidationContext(ISSUER, CLIENT_ID, NONCE, null)

        val hourAgo = System.currentTimeMillis() - (60 * 60) * 1000
        val claims =
            defaultIdTokenClaims()
                .expirationTime(Date(hourAgo))
                .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertLeft { assertErrorMessage("Expired JWT", it.message) }
        }
    }

    companion object {
        const val ID_TOKEN_KEY_ID = "testKey"
        const val ISSUER = "https://issuer.example.com"
        const val CLIENT_ID = "client1"
        const val USER_UUID = "userUuid"
        const val USER_ID = "12345"
        const val NONCE = "nonce1234"
    }
}
