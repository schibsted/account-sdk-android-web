package com.schibsted.account.webflows.persistence.compat

import com.google.gson.Gson
import com.nimbusds.jose.Payload
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.createJws
import com.schibsted.account.webflows.token.IdTokenClaims
import com.schibsted.account.webflows.token.IdTokenValidatorTest
import com.schibsted.account.webflows.token.MigrationUserTokens
import com.schibsted.account.webflows.token.UserTokens
import com.schibsted.account.webflows.user.MigrationStoredUserSession
import com.schibsted.account.webflows.user.StoredUserSession
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class LegacySessionStorageTest {
    private val gson = Gson()
    private val jwk: JWK

    private val clientId = "client1"

    init {
        jwk = RSAKeyGenerator(2048)
            .keyID(IdTokenValidatorTest.idTokenKeyId)
            .generate()
    }

    private fun legacyTokensToStoredUserSession(
        legacySession: LegacySession,
        idTokenClaims: IdTokenClaims
    ): MigrationStoredUserSession {
        return MigrationStoredUserSession(
            clientId,
            MigrationUserTokens(
                legacySession.tokens.accessToken,
                legacySession.tokens.refreshToken ?: "",
                legacySession.tokens.idToken,
                idTokenClaims.copy(aud = emptyList())
            ),
            Date(legacySession.lastActive)
        )
    }

    private fun createLegacySession(clientId: String, idTokenClaims: IdTokenClaims): LegacySession {
        val accessTokenData = gson.toJson(mapOf("client_id" to clientId))
        val accessToken = createJws(jwk.toRSAKey(), "test_key", Payload(accessTokenData))

        val idTokenData = gson.toJson(
            mapOf(
                "iss" to idTokenClaims.iss,
                "sub" to idTokenClaims.sub,
                "legacy_user_id" to idTokenClaims.userId,
                "exp" to idTokenClaims.exp,
                "iat" to 1234,
                "nonce" to idTokenClaims.nonce
            )
        )
        val idToken = createJws(jwk.toRSAKey(), "test_key", Payload(idTokenData))

        return LegacySession(
            1234,
            idTokenClaims.userId,
            LegacyUserTokens(accessToken, "refresh_token", idToken)
        )
    }

    @Test
    fun testNoExistingLegacyData() {
        val legacyTokenStorage = mockk<LegacyTokenStorage> {
            every { get() } returns emptyList()
        }
        val legacySessionStorage = LegacySessionStorage(legacyTokenStorage)
        assertNull(legacySessionStorage.get(clientId))
    }

    @Test
    fun testExistingLegacyData() {
        val existingSession = createLegacySession(clientId, Fixtures.idTokenClaims)
        val legacyTokenStorage = mockk<LegacyTokenStorage> {
            every { get() } returns listOf(existingSession)
        }

        val legacySessionStorage = LegacySessionStorage(legacyTokenStorage)
        val expectedSession = legacyTokensToStoredUserSession(existingSession, Fixtures.idTokenClaims)
        assertEquals(expectedSession, legacySessionStorage.get(clientId))
    }

    @Test
    fun testExistingLegacyDataWithoutRefreshTokenIsIgnored() {
        val existingSession = createLegacySession(clientId, Fixtures.idTokenClaims)
        val existingSessionWithoutRefreshToken =
            existingSession.copy(tokens = existingSession.tokens.copy(refreshToken = null))
        val legacyTokenStorage = mockk<LegacyTokenStorage> {
            every { get() } returns listOf(existingSessionWithoutRefreshToken)
        }

        val legacySessionStorage = LegacySessionStorage(legacyTokenStorage)
        assertNull(legacySessionStorage.get(clientId))
    }

    @Test
    fun testExistingLegacyDataWithoutIdTokenIsNotIgnored() {
        val existingSession = createLegacySession(clientId, Fixtures.idTokenClaims)
        val existingSessionWithoutIdToken =
            existingSession.copy(tokens = existingSession.tokens.copy(idToken = null))
        val legacyTokenStorage = mockk<LegacyTokenStorage> {
            every { get() } returns listOf(existingSessionWithoutIdToken)
        }

        val legacySessionStorage = LegacySessionStorage(legacyTokenStorage)
        assertNotNull(legacySessionStorage.get(clientId))
    }

    @Test
    fun testGetReturnsNewestTokens() {
        val oldestSession = createLegacySession(clientId, Fixtures.idTokenClaims)
        val newestSession = oldestSession.copy(lastActive = oldestSession.lastActive + 10)
        val legacyTokenStorage = mockk<LegacyTokenStorage> {
            every { get() } returns listOf(oldestSession, newestSession)
        }

        val legacySessionStorage = LegacySessionStorage(legacyTokenStorage)
        val expectedSession = legacyTokensToStoredUserSession(newestSession, Fixtures.idTokenClaims)
        assertEquals(expectedSession, legacySessionStorage.get(clientId))
    }

    @Test
    fun testGetDiscardsTokenForOtherClient() {
        val existingSession = createLegacySession(clientId, Fixtures.idTokenClaims)
        val legacyTokenStorage = mockk<LegacyTokenStorage> {
            every { get() } returns listOf(existingSession)
        }

        val legacySessionStorage = LegacySessionStorage(legacyTokenStorage)
        assertNull(legacySessionStorage.get("other_client_id"))
    }

    @Test
    fun testRemove() {
        val legacyTokenStorage = mockk<LegacyTokenStorage>(relaxUnitFun = true)
        val legacySessionStorage = LegacySessionStorage(legacyTokenStorage)
        legacySessionStorage.remove()
        verify { legacyTokenStorage.remove() }
    }
}
