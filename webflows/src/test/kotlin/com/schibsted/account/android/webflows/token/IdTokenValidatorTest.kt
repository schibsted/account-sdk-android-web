package com.schibsted.account.android.webflows.token

import com.schibsted.account.android.testutil.assertLeft
import com.schibsted.account.android.testutil.assertRight
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.schibsted.account.android.webflows.jose.AsyncJwks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

private class TestJwks(private val jwks: JWKSet?) : AsyncJwks {
    override fun fetch(callback: (JWKSet?) -> Unit) {
        callback(jwks)
    }
}

class IdTokenValidatorTest {
    private val jwk: JWK
    private val jwks: JWKSet

    init {
        jwk = RSAKeyGenerator(2048)
            .keyID(idTokenKeyId)
            .generate()
        jwks = JWKSet(jwk)
    }

    private fun defaultIdTokenClaims(): JWTClaimsSet.Builder {
        return JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(clientId)
            .expirationTime(Date(System.currentTimeMillis() + 5000))
            .subject(userUuid)
            .claim("legacy_user_id", userId)
            .claim("nonce", nonce)
    }

    private fun createIdToken(claims: JWTClaimsSet): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(idTokenKeyId)
            .build()
        val jwsObject = JWSObject(header, Payload(claims.toJSONObject()))

        jwsObject.sign(RSASSASigner(jwk.toRSAKey()))
        return jwsObject.serialize()
    }

    private fun idTokenClaims(claims: JWTClaimsSet): IdTokenClaims {
        return IdTokenClaims(
            claims.issuer,
            claims.subject,
            claims.getStringClaim("legacy_user_id"),
            claims.audience,
            (claims.expirationTime.time / 1000).toInt(),
            claims.getStringClaim("nonce"),
            claims.getStringListClaim("amr")
        )
    }

    private fun assertErrorMessage(expectedSubstring: String, actualMessage: String) {
        assertTrue(
            "'${expectedSubstring}' not in '${actualMessage}'",
            actualMessage.contains(expectedSubstring)
        )
    }

    @Test
    fun testAcceptsValidWithoutAMR() {
        val context = IdTokenValidationContext(issuer, clientId, nonce, null)
        val claims = defaultIdTokenClaims().build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertRight { assertEquals(idTokenClaims(claims), it) }
        }
    }

    @Test
    fun testAcceptsValidWithExpectedAMR() {
        val expectedAmrValue = "testValue"
        val context = IdTokenValidationContext(issuer, clientId, nonce, expectedAmrValue)
        val claims = defaultIdTokenClaims()
            .claim("amr", listOf(expectedAmrValue, "otherValue"))
            .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertRight { assertEquals(idTokenClaims(claims), it) }
        }
    }

    @Test
    fun testRejectMissingExpectedAMRInIdTokenWithoutAMR() {
        val context = IdTokenValidationContext(issuer, clientId, nonce, "testValue")

        for (amr in listOf(null, emptyList(), listOf("otherValue1", "otherValue2"))) {
            val claims = defaultIdTokenClaims()
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
        val context = IdTokenValidationContext(issuer, clientId, nonce, null)

        for (nonce in listOf(null, "otherNonce")) {
            val claims = defaultIdTokenClaims()
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
        val context = IdTokenValidationContext(issuer, clientId, nonce, null)

        val claims = defaultIdTokenClaims()
            .issuer("https://other.example.com")
            .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertLeft { assertErrorMessage("Invalid issuer", it.message) }
        }
    }

    @Test
    fun testAcceptsIssuerWithTrailingSlash() {
        val issuerWithSlash = "$issuer/"
        val testData = listOf(
            issuer to issuer,
            issuer to issuerWithSlash,
            issuerWithSlash to issuer,
            issuerWithSlash to issuerWithSlash
        )

        for ((idTokenIssuer, expectedIssuer) in testData) {
            val context = IdTokenValidationContext(expectedIssuer, clientId, nonce, null)
            val claims = defaultIdTokenClaims()
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
        val context = IdTokenValidationContext(issuer, clientId, nonce, null)

        val claims = defaultIdTokenClaims()
            .audience("otherClient")
            .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertLeft { assertErrorMessage("audience", it.message) }
        }
    }

    @Test
    fun testRejectsExpiredIdToken() {
        val context = IdTokenValidationContext(issuer, clientId, nonce, null)

        val hourAgo = System.currentTimeMillis() - (60 * 60) * 1000
        val claims = defaultIdTokenClaims()
            .expirationTime(Date(hourAgo))
            .build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) { result ->
            result.assertLeft { assertErrorMessage("Expired JWT", it.message) }
        }
    }

    companion object {
        const val idTokenKeyId = "testKey"
        const val issuer = "https://issuer.example.com"
        const val clientId = "client1"
        const val userUuid = "userUuid"
        const val userId = "12345"
        const val nonce = "nonce1234"
    }
}
