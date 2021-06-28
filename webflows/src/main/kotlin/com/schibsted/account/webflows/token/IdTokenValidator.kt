package com.schibsted.account.webflows.token

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.schibsted.account.webflows.jose.AsyncJwks
import com.schibsted.account.webflows.util.Either
import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right

internal sealed class IdTokenValidationError {
    abstract val message: String

    data class FailedValidation(override val message: String) : IdTokenValidationError()
    data class UnexpectedError(override val message: String) : IdTokenValidationError()
}

internal object IdTokenValidator {
    fun validate(
        idToken: String,
        jwks: AsyncJwks,
        validationContext: IdTokenValidationContext,
        callback: (Either<IdTokenValidationError, IdTokenClaims>) -> Unit
    ) {
        // https://openid.net/specs/openid-connect-core-1_0.html#IDTokenValidation
        jwks.fetch { fetchedJwks ->
            if (fetchedJwks == null) {
                callback(Left(IdTokenValidationError.UnexpectedError("Failed to fetch JWKS to validate ID Token")))
            } else {
                callback(validate(idToken, fetchedJwks, validationContext))
            }
        }
    }

    private fun validate(
        idToken: String,
        jwks: JWKSet,
        validationContext: IdTokenValidationContext
    ): Either<IdTokenValidationError, IdTokenClaims> {
        val jwtProcessor = DefaultJWTProcessor<IdTokenValidationContext>()
        val keySelector = JWSVerificationKeySelector<IdTokenValidationContext>(
            JWSAlgorithm.RS256,
            ImmutableJWKSet<IdTokenValidationContext>(jwks)
        )
        jwtProcessor.jwsKeySelector = keySelector

        val expectedClaims = JWTClaimsSet.Builder()
            .claim("nonce", validationContext.nonce)
            .build()
        jwtProcessor.jwtClaimsSetVerifier = IdTokenClaimsVerifier(
            validationContext.clientId,
            expectedClaims,
            setOf("sub", "exp")
        )

        val claims: JWTClaimsSet
        try {
            claims = jwtProcessor.process(idToken, validationContext)
        } catch (e: BadJWTException) {
            return Left(
                IdTokenValidationError.FailedValidation(e.message ?: "Failed to verify ID Token")
            )
        }

        return Right(
            IdTokenClaims(
                claims.issuer,
                claims.subject,
                claims.getStringClaim("legacy_user_id"),
                claims.audience,
                (claims.expirationTime.time / 1000).toInt(),
                claims.getStringClaim("nonce"),
                claims.getStringListClaim("amr")
            )
        )
    }
}

internal class IdTokenClaimsVerifier(
    clientId: String,
    exactMatchClaims: JWTClaimsSet,
    requiredClaims: Set<String>
) : DefaultJWTClaimsVerifier<IdTokenValidationContext>(clientId, exactMatchClaims, requiredClaims) {
    override fun verify(claims: JWTClaimsSet?, context: IdTokenValidationContext?) {
        super.verify(claims, context)

        // verify issuer, allowing trailing slash
        if (context?.issuer?.removeSuffix("/") != claims?.issuer?.removeSuffix("/")) {
            throw BadJWTException("Invalid issuer '${claims?.issuer}'")
        }

        // verify AMR
        if (!contains(claims?.getStringListClaim("amr"), context?.expectedAmr)) {
            throw BadJWTException("Missing expected AMR value: ${context?.expectedAmr}")
        }
    }

    private fun contains(values: List<String>?, value: String?): Boolean {
        val needle = value ?: return true // no value to search for
        val haystack = values ?: return false // no values to search among

        return haystack.contains(needle)
    }
}

internal data class IdTokenValidationContext(
    val issuer: String,
    val clientId: String,
    val nonce: String?,
    val expectedAmr: String?
) : SecurityContext
