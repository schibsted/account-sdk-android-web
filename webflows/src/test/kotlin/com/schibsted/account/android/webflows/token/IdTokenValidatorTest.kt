package com.schibsted.account.android.webflows.token

import android.security.keystore.KeyProperties
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.schibsted.account.android.webflows.jose.AsyncJwks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.util.*

private class TestJwks(private val jwks: JWKSet?) : AsyncJwks {
    override fun fetch(callback: (JWKSet?) -> Unit) {
        callback(jwks)
    }
}

class IdTokenValidatorTest {
    private val keyPair: KeyPair
    private val jwks: JWKSet

    init {
        keyPair = generateKeyPair()
        val jwk = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .keyID(idTokenKeyId)
            .build()
        jwks = JWKSet(jwk)
    }

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        generator.initialize(2048, SecureRandom())
        return generator.genKeyPair()
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

        jwsObject.sign(RSASSASigner(keyPair.private))
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

    private fun assertErrorMessage(expectedSubstring: String, result: IdTokenValidationResult) {
        assertTrue(result is IdTokenValidationResult.Failure)
        val errorMessage = (result as IdTokenValidationResult.Failure).message
        assertTrue(
            "'${expectedSubstring}' not in '${errorMessage}'",
            errorMessage.contains(expectedSubstring)
        )
    }

    private fun assertClaims(claims: JWTClaimsSet, result: IdTokenValidationResult) {
        assertTrue(result is IdTokenValidationResult.Success)
        val verifiedClaims = (result as IdTokenValidationResult.Success).claims
        assertEquals(idTokenClaims(claims), verifiedClaims)
    }

    @Test
    fun testAcceptsValidWithoutAMR() {
        val context = IdTokenValidationContext(issuer, clientId, nonce, null)
        val claims = defaultIdTokenClaims().build()
        val idToken = createIdToken(claims)
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) {
            assertClaims(claims, it)
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
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) {
            assertClaims(claims, it)
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
            IdTokenValidator.validate(idToken, TestJwks(jwks), context) {
                assertErrorMessage("Missing expected AMR value", it)
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
            IdTokenValidator.validate(idToken, TestJwks(jwks), context) {
                assertErrorMessage("nonce", it)
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
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) {
            assertErrorMessage("Invalid issuer", it)
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
            IdTokenValidator.validate(idToken, TestJwks(jwks), context) {
                assertClaims(claims, it)
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
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) {
            assertErrorMessage("audience", it)
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
        IdTokenValidator.validate(idToken, TestJwks(jwks), context) {
            assertErrorMessage("Expired JWT", it)
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
