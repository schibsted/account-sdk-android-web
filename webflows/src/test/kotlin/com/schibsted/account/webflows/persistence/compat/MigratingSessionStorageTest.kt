package com.schibsted.account.webflows.persistence.compat

import android.util.Log
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.await
import com.schibsted.account.testutil.withServer
import com.schibsted.account.webflows.api.SchibstedAccountApi
import com.schibsted.account.webflows.client.AuthState
import com.schibsted.account.webflows.persistence.SessionStorage
import com.schibsted.account.webflows.token.TokenHandler
import com.schibsted.account.webflows.token.TokenRequestResult
import com.schibsted.account.webflows.token.UserTokensResult
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either.Right
import io.mockk.*
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.*


class MigratingSessionStorageTest {
    private val newClientId = "newClientId"
    private val legacyClientId = "legacyClientId"

    @Test
    fun testStoreOnlyWritesToNewStorage() {
        val legacyStorage = mockk<LegacySessionStorage>(relaxUnitFun = true)
        val newStorage = mockk<SessionStorage>(relaxUnitFun = true)

        val migratingStorage = MigratingSessionStorage(
            mockk(),
            newStorage,
            legacyStorage,
            legacyClientId
        )
        val userSession = StoredUserSession(newClientId, Fixtures.userTokens, Date())
        migratingStorage.save(StoredUserSession(newClientId, Fixtures.userTokens, Date()))

        verify { newStorage.save(userSession) }
        verify { legacyStorage wasNot Called }
    }

    @Test
    fun testRemoveOnlyRemovesFromNewStorage() {
        val legacyStorage = mockk<LegacySessionStorage>(relaxUnitFun = true)
        val newStorage = mockk<SessionStorage>(relaxUnitFun = true)

        val migratingStorage = MigratingSessionStorage(
            mockk(),
            newStorage,
            legacyStorage,
            legacyClientId
        )
        migratingStorage.remove(newClientId)

        verify { newStorage.remove(newClientId) }
        verify { legacyStorage wasNot Called }
    }

    @Test
    fun testGetPrefersNewStorage() {
        val legacyStorage = mockk<LegacySessionStorage>(relaxUnitFun = true)

        val userSession = StoredUserSession(newClientId, Fixtures.userTokens, Date())
        val newStorage = mockk<SessionStorage>()
        every { newStorage.get(newClientId, any()) } answers {
            val callback = secondArg<(StoredUserSession?) -> Unit>()
            callback(userSession)
        }

        val migratingStorage = MigratingSessionStorage(
            mockk(),
            newStorage,
            legacyStorage,
            legacyClientId
        )

        migratingStorage.get(newClientId) {
            assertEquals(userSession, it)
        }

        verify { legacyStorage wasNot Called }
    }

    @Test
    fun testGetMigratesExistingLegacySession() {
        mockkStatic(Log::class)
        every { Log.v(any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        val authCode = "test_auth_code"
        fun withApiResponseWrapper(responseData: String): String {
            return """
        {
            "name": "SPP Container",
            "version": "0.2",
            "api": 2,
            "code": 200,
            "data": $responseData
        }
        """.trimIndent()
        }

        val legacyStorage = mockk<LegacySessionStorage>(relaxUnitFun = true)
        val legacyUserSession = StoredUserSession(legacyClientId, Fixtures.userTokens, Date())
        every { legacyStorage.get(legacyClientId) } returns legacyUserSession

        val newStorage = mockk<SessionStorage>(relaxUnitFun = true)
        every { newStorage.get(newClientId, any()) } answers {
            val callback = secondArg<(StoredUserSession?) -> Unit>()
            callback(null)
        }

        val codeExchangeResponse = MockResponse().setResponseCode(200)
            .setBody(withApiResponseWrapper("""{"code": "$authCode"}"""))

        withServer(codeExchangeResponse) { server ->
            val schaccApi = SchibstedAccountApi(server.url("/"), Fixtures.httpClient)
            await { done ->
                val tokenHandler = mockk<TokenHandler>() {
                    every {
                        makeTokenRequest(
                            authCode,
                            AuthState("", null, null, null),
                            any()
                        )
                    } answers {
                        val callback = thirdArg<(TokenRequestResult) -> Unit>()
                        callback(Right(UserTokensResult(Fixtures.userTokens, null, 10)))
                    }
                }
                val client =
                    Fixtures.getClient(clientConfiguration = Fixtures.clientConfig.copy(clientId = newClientId), tokenHandler = tokenHandler, schibstedAccountApi = schaccApi)

                val migratingStorage = MigratingSessionStorage(
                    client,
                    newStorage,
                    legacyStorage,
                    legacyClientId
                )

                val expectedMigratedSession = StoredUserSession(newClientId, Fixtures.userTokens, Date())
                migratingStorage.get(newClientId) {
                    assertEquals(expectedMigratedSession.clientId, it!!.clientId)
                    assertEquals(expectedMigratedSession.userTokens, it.userTokens)

                    verify { newStorage.get(newClientId, any()) }
                    verify { legacyStorage.get(legacyClientId) }
                    verify { newStorage.save(match {
                        expectedMigratedSession.clientId == it.clientId &&
                        expectedMigratedSession.userTokens == it.userTokens
                    }) }
                    verify { legacyStorage.remove() }

                    done()
                }
            }
        }
    }

    @Test
    fun testGetReturnsNullIfNoSessionExists() {
        val legacyStorage = mockk<LegacySessionStorage>()
        every { legacyStorage.get(legacyClientId) } returns null
        val newStorage = mockk<SessionStorage>()
        every { newStorage.get(newClientId, any()) } answers {
            val callback = secondArg<(StoredUserSession?) -> Unit>()
            callback(null)
        }

        val migratingStorage = MigratingSessionStorage(
            mockk(),
            newStorage,
            legacyStorage,
            legacyClientId
        )
        migratingStorage.get(newClientId) {
            assertNull(it)
        }

        verify { newStorage.get(newClientId, any()) }
        verify { legacyStorage.get(legacyClientId) }
    }
}
